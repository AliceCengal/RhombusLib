package com.cengallut.cardreader

import scala.collection.JavaConversions._

import android.content.{Intent, IntentFilter}
import android.util.Log
import android.app.Activity
import android.os.{HandlerThread, Message, Handler, Bundle}
import android.widget.LinearLayout

import com.jjoe64.graphview.GraphView.GraphViewData
import com.jjoe64.graphview.{GraphViewSeries, LineGraphView, GraphView}
import me.cosmodro.app.rhombus.{HeadsetStateReceiver, AudioMonitor, RhombusActivity}

class Main extends Activity with RhombusActivity {
  import Main._

  lazy val graph = new LineGraphView(this, "Swipe data")

  lazy val receiver = new SwipeReceiver(graph)

  lazy val broadcastReceiver = new HeadsetStateReceiver(this)

  override def setDongleReady(state: Boolean): Unit = {
    if (state) {
      log("Dongle is ready")

      val r = receiver // force creation

      val pollLoop = new Runnable {
        override def run(): Unit = {

          val monitor = new AudioMonitor(r)
          while (true) monitor.monitor()

        }
      }

      val thread = new HandlerThread("poll thread")
      thread.start()
      val h = new Handler(thread.getLooper)
      h.post(pollLoop)

    } else {
      log("Dongle is unready")
    }
  }

  override def onCreate(saved: Bundle): Unit = {
    super.onCreate(saved)
    setContentView(R.layout.activity_main)
    val container = findViewById(R.id.container).asInstanceOf[LinearLayout]
    container.addView(graph)

    graph.addSeries(new GraphViewSeries(
      Array(
        new GraphViewData(1, 2),
        new GraphViewData(2, 4),
        new GraphViewData(3, 3))))
  }


  override def onResume(): Unit = {
    super.onResume()
    registerReceiver(
      broadcastReceiver,
      new IntentFilter(Intent.ACTION_HEADSET_PLUG))
  }

  override def onPause(): Unit = {
    super.onPause()
    unregisterReceiver(broadcastReceiver)
  }
}

object Main {

  class SwipeReceiver(graph: GraphView) extends Handler {

    override def handleMessage(msg: Message): Unit = {
      msg.what match {
        case 3 /* DATA */ =>
          log("Received Data message")
          msg.obj match {
            case l: java.util.List[Integer] =>
              log(s"Received ${l.size()} points")

              val data = l.zipWithIndex.map {
                case (point, i) => new GraphViewData(i, point.toDouble) }

              graph.removeAllSeries()
              graph.addSeries(new GraphViewSeries(data.toArray))

            case _ => log("Received wrong data type")
          }

        case 0 =>

        case n =>
          log(s"Received message $n")

      }
    }
  }

  def log(msg: String): Unit = Log.i("CardReader", msg)

}