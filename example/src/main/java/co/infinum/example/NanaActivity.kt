package co.infinum.example

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

/**
 * @intro
 * @author sunhee
 * @date 2019/12/14
 */
class NanaActivity:AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nana)
        savedInstanceState ?: supportFragmentManager.beginTransaction()
                .replace(R.id.container, CameraShootFragment.newInstance())
                .commit()
    }


}