package org.y20k.escapepod

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import org.y20k.escapepod.helpers.LogHelper

class MainActivity: AppCompatActivity() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(MainActivity::class.java)


    /* Main class variables */
    private lateinit var navHostFragment: NavHostFragment


    /* Overrides onCreate from AppCompatActivity */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // set up views
        setContentView(R.layout.activity_main)
        // navHostFragment  = supportFragmentManager.findFragmentById(R.id.main_container) as NavHostFragment

        // navHostFragment.findNavController().navigate(R.id.podcast_player_fragment)


    }



}