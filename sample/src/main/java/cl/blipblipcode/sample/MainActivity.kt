package cl.blipblipcode.sample

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Actividad mínima del sample.
 *
 * La clase existe para que R8 tenga algo que ofuscar en los builds de release: sin ella
 * el `mapping.txt` que genera android-bundle-artifacts sale vacío.
 */
class MainActivity : AppCompatActivity(R.layout.activity_main) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<TextView>(R.id.sample_text).text = getString(R.string.sample_message)
    }
}
