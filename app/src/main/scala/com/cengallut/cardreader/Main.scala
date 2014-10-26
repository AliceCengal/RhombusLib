package com.cengallut.cardreader

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import com.jjoe64.graphview.{LineGraphView, GraphView}
import me.cosmodro.app.rhombus.RhombusActivity

class Main extends Activity with RhombusActivity {

  override def setDongleReady(state: Boolean): Unit = {}

  override def onCreate(saved: Bundle): Unit = {
    super.onCreate(saved)
    setContentView(R.layout.activity_main)
    val container = findViewById(R.id.container).asInstanceOf[LinearLayout]
    val g: GraphView = new LineGraphView(this, "Swipe data")
    container.addView(g)



  }



}