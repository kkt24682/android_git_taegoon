package com.naver.naverspeech.client;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.naverspeech.client.utils.AudioWriterPCM;
import com.naver.speech.clientapi.SpeechConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";
	private static final String CLIENT_ID = "WHH22WbOjGzLdxAUAfy3"; // "내 애플리케이션"에서 Client ID를 확인해서 이곳에 적어주세요.
	private static final String CLIENT_SECRET = "Hy9NFXclcG";
	private static final String TRANSLATE_URL = "https://openapi.naver.com/v1/language/translate";
	private static final String TTS_URL =  "https://openapi.naver.com/v1/voice/tts.bin";
	private static final SpeechConfig SPEECH_CONFIG = SpeechConfig.OPENAPI_KR; // or SpeechConfig.OPENAPI_EN

	private RecognitionHandler handler;
	private NaverRecognizer naverRecognizer;

	private TextView trsView;
	private TextView txtResult;
	public ImageButton btnStart;
	private MediaPlayer mp;

	public String trsRes;
	private String mResult;
	private String mTrsParam;
	private String mTTSParam;
	private boolean mPressState = false;

	private AudioWriterPCM writer;



	private boolean isRunning;
	private ArrayAdapter<CharSequence> spinnerAdapter;

	// Handle speech recognition Messages.
	private void handleMessage(final Message msg) {
		switch (msg.what) {
			case R.id.clientReady:
				// Now an user can speak.
				txtResult.setText("Connected");
				writer = new AudioWriterPCM(
						Environment.getExternalStorageDirectory().getAbsolutePath() + "/NaverSpeechTest");
				Log.d(TAG, Environment.getExternalStorageDirectory().getAbsolutePath().toString());
				writer.open("Test");
				break;

			case R.id.audioRecording:
				writer.write((short[]) msg.obj);
				break;

			case R.id.partialResult:
				// Extract obj property typed with String.
				mResult = (String) (msg.obj);
				txtResult.setText("* 음성인식결과::" + mResult);
				break;
			case R.id.endPointDetected:
				if(!mPressState)
				{
					Toast.makeText(MainActivity.this, "음성인식종료!!", Toast.LENGTH_SHORT).show();
				}
				break;
			case R.id.finalResult:  //음성인식 완료 시점
				// Extract obj property typed with String array.
				// The first element is recognition result for speech.
				String[] results = (String[]) msg.obj;
				mResult = results[0];
				txtResult.setText("* 음성인식결과::" + mResult);

				/**
				 * API메소드 호출 쓰레드
				 * trsRes 기계번역결과
				 * mResult 음성인식결과
				 */
				new Thread(new Runnable() {
					@Override
					public void run() {
						trsRes = callTranslateAPI(mResult);        //기계번역 API 메소드
						if (trsRes == null) trsRes = "";
						if (!trsRes.equals("")) {
							callTTSAPI(trsRes);                    //음성합성 API 메소드
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									trsView.setText("* 기계번역결과::" + trsRes);
								}
							});
						}
					}
				}).start();

				break;

			case R.id.recognitionError:
				if (writer != null) {
					writer.close();
				}

				mResult = "Error code : " + msg.obj.toString();
				txtResult.setText(mResult);
				//btnStart.setText(com.naver.naverspeech.client.R.string.str_start);
				btnStart.setEnabled(true);
				isRunning = false;
				break;

			case R.id.clientInactive:
				if (writer != null) {
					writer.close();
				}

				//btnStart.setText(com.naver.naverspeech.client.R.string.str_start);
				btnStart.setEnabled(true);
				isRunning = false;
				break;
		}
	}

	@Override
	public void onBackPressed() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setPositiveButton("확인", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int which)
			{
				// TODO Auto-generated method stub
				MainActivity.this.finish();
			}
		}).setNegativeButton("취소", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int which)
			{
				// TODO Auto-generated method stub
				dialog.cancel();
			}
		}).setMessage("프로그램을 종료 시키겠습니까?").setTitle("종료");

		builder.show();
	}

	//기계번역 API 호출 메소드
	private String callTranslateAPI (String src) {
		String result = null;
		try {
			String param = mTrsParam + URLEncoder.encode(src, "UTF-8");
			URL url = new URL(TRANSLATE_URL);
			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setRequestProperty("X-Naver-Client-Id", CLIENT_ID);
			con.setRequestProperty("X-Naver-Client-Secret", CLIENT_SECRET);
			con.setDoOutput(true);
			con.setDoInput(true);
			// Api  호출
			OutputStreamWriter wr = new OutputStreamWriter(con.getOutputStream());
			wr.write(param);
			wr.flush();

			//TO-DO Api  서버 응답 처리
			String trsData = "";
			int responseCode = con.getResponseCode();
			if (responseCode == 200) {
				InputStream is = con.getInputStream();
				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				String line = "";
				trsData = "";
				while ((line = br.readLine()) != null) {
					trsData += line;
				}
				br.close();
				is.close();
				result = trsData;
			} else {
				Log.d("callTranslateAPI", "###### responseCode:" + responseCode);
			}

			JSONObject json = new JSONObject(trsData);
			try {
				JSONObject json1 = json.getJSONObject("message");
				JSONObject json2 = json1.getJSONObject("result");
				result = json2.getString("translatedText");
			} catch (Exception e) {
				e.printStackTrace();
			}

			con.disconnect();



		} catch (Exception e) {
			e.printStackTrace();
		}

		return result;
	}
	//음성합성 API 호출 메소드
	private void callTTSAPI(String src) {
		try {
			String param = mTTSParam + URLEncoder.encode(src, "UTF-8");
			URL url = new URL(TTS_URL);
			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setRequestProperty("X-Naver-Client-Id", CLIENT_ID);
			con.setRequestProperty("X-Naver-Client-Secret", CLIENT_SECRET);
			con.setDoOutput(true);
			con.setDoInput(true);
			OutputStreamWriter wr = new OutputStreamWriter(con.getOutputStream());
			wr.write(param);
			wr.flush();

			//TO-DO Api  서버 응답 처리
			int responseCode = con.getResponseCode();
			if (responseCode == 200) {

				//TO-DO 음성합성 API 호출 성공시 파일 생성 처리
				InputStream is = con.getInputStream();
				int read = 0;
				byte[] bytes = new byte[1024];
				String tempname = Long.valueOf(new Date().getTime()).toString();
				File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + tempname + ".mp3");
				f.createNewFile();
				OutputStream outputStream = new FileOutputStream(f);
				while ((read = is.read(bytes)) != -1) {
					outputStream.write(bytes, 0, read);
				}
				is.close();

				//생성된 파일 MediaPlayer에서 재생
				if (f.exists()) {
					mp = new MediaPlayer();
					mp.setDataSource(f.getAbsolutePath());
					mp.prepare();
					mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
						@Override
						public void onPrepared(MediaPlayer mp) {
							mp.start();
							Log.d("TTS", "###### 파일 재생 ######");
							mp.setVolume(1.0f, 1.0f);
						}
					});
					mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
						@Override
						public void onCompletion(MediaPlayer mp) {
							mp.stop();
							Log.d("TTS", "###### 파일 종료 ######");
							mp.reset();
						}
					});
				} else {
					Log.d("TTS", "###### 파일 없다 ######");
				}

			} else {
				System.out.println("api error!! status code =" + responseCode);
			}
			con.disconnect();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

//	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
		setContentView(R.layout.activity_main);
		setSpinner();


		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setDisplayShowHomeEnabled(false);
		actionBar.setDisplayShowCustomEnabled(true);

		actionBar.setCustomView(R.layout.actionbar_layout);



		trsView = (TextView) findViewById(R.id.trsView);
		txtResult = (TextView) findViewById(R.id.txt_result);
		btnStart = (ImageButton) findViewById(R.id.btn_start);

		handler = new RecognitionHandler(this);
		naverRecognizer = new NaverRecognizer(this, handler, CLIENT_ID, SPEECH_CONFIG);


		/**버튼 click 조작*/
		/*btnStart.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (!isRunning) {
					// Start button is pushed when SpeechRecognizer's state is inactive.
					// Run SpeechRecongizer by calling recognize().
					mResult = "";
					txtResult.setText("Connecting...");
					//btnStart.setText(com.naver.naverspeech.client.R.string.str_listening);
					isRunning = true;

					naverRecognizer.recognize();
				} else {
					// This flow is occurred by pushing start button again
					// when SpeechRecognizer is running.
					// Because it means that a user wants to cancel speech
					// recognition commonly, so call stop().
					btnStart.setEnabled(false);

					naverRecognizer.getSpeechRecognizer().stop();
				}
			}
		});*/

		/** 버튼 touch 조작*/
		btnStart.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				int key = motionEvent.getAction();
				switch (key) {
					case MotionEvent.ACTION_DOWN:
						mPressState = true;
						naverRecognizer.recognize();
						Toast.makeText(MainActivity.this, "음성인식시작!!", Toast.LENGTH_SHORT).show();

						break;
					case MotionEvent.ACTION_UP:
						mPressState = false;
						naverRecognizer.getSpeechRecognizer().stop();

						break;
				}
				return false;
			}
		});
	}


	private void setSpinner() {

		Spinner spinner = (Spinner) findViewById(R.id.spinner);
		spinnerAdapter = ArrayAdapter.createFromResource(MainActivity.this, R.array.spnItem_array, R.layout.spinner_item);
		spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(spinnerAdapter);
		spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
//				Long spinnerPos = parent.getItemIdAtPosition(pos);
//				Log.d("spinner", String.valueOf(pos));
				switch (pos) {
					case 0:        // 한-영
						mTrsParam = "source=ko&target=en&text=";
						mTTSParam = "speaker=clara&speed=0&text=";
						break;
					case 1:        // 한-일
						mTrsParam = "source=ko&target=ja&text=";
						mTTSParam = "speaker=yuri&speed=0&text=";
						break;
					case 2:        // 한-중
						mTrsParam = "source=ko&target=zh-CN&text=";
						mTTSParam = "speaker=meimei&speed=0&text=";
						break;
					default:
						mTrsParam = "source=ko&target=en&text=";
						mTTSParam = "speaker=clara&speed=0&text=";
				}



			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub

			}
		});

	}



	@Override
	protected void onResume() {
		super.onResume();
		// initialize() must be called on resume time.
		naverRecognizer.getSpeechRecognizer().initialize();

		mResult = "";
		//txtResult.setText("");
		btnStart.setEnabled(true);
	}

	@Override
	protected void onPause() {
		super.onPause();
		// release() must be called on pause time.
		naverRecognizer.getSpeechRecognizer().stopImmediately();
		naverRecognizer.getSpeechRecognizer().release();
		isRunning = false;
	}

	// Declare handler for handling SpeechRecognizer thread's Messages.
	static class RecognitionHandler extends Handler {
		private final WeakReference<MainActivity> mActivity;

		RecognitionHandler(MainActivity activity) {
			mActivity = new WeakReference<MainActivity>(activity);
		}

		@Override
		public void handleMessage(Message msg) {
			MainActivity activity = mActivity.get();
			if (activity != null) {
				activity.handleMessage(msg);
			}
		}
	}
}