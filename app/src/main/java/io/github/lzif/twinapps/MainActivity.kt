package io.github.lzif.twinapps

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.lzif.twinapps.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TODO: main screen - list personal-profile apps on one tab,
        // work-profile apps on the other. See spec section 7 (App inventory).
        binding.statusText.text = getString(R.string.status_not_provisioned)
    }
}
