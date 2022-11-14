package com.eggheadgames.sirenapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.eggheadgames.siren.Siren

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Siren.getInstance(this)
    }
}
