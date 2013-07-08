package br.com.verde.androvision;

import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class MainActivity extends Activity {

	private FrameLayout layout;
	private OpencvView faceView;
	private Preview mPreview;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Escondendo a moldura
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// Create our Preview View and set it as the content of our activity.
		try {
			layout = new FrameLayout(this);
			faceView = new OpencvView(this);
			mPreview = new Preview(this, faceView);
			layout.addView(mPreview);
			layout.addView(faceView);
			setContentView(layout);
		} catch (IOException e) {
			e.printStackTrace();
			new AlertDialog.Builder(this).setMessage(e.getMessage()).create().show();
		}
	}
}