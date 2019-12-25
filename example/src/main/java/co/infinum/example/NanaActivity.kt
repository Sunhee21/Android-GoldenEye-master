package co.infinum.example

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.WindowManager

/**
 * @intro
 * @author sunhee
 * @date 2019/12/14
 */
class NanaActivity:AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_nana)
        savedInstanceState ?: supportFragmentManager.beginTransaction()
                .replace(R.id.container, CameraShootFragment.newInstance())
                .commit()
    }


}