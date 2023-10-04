package com.fisify.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class Main extends AppCompatActivity
{
	private static final String TAG = "Main";

	Context context;
	Window window;
	WebView web;

	private static final int SPLASHSCREEN_DELAY_AFTER_PAGE_LOADED = 2000;
	private static final int RC_SIGN_IN = 9001;

	private GoogleSignInClient mGoogleSignInClient;

	private final String WEBVIEW_PRODUCTION_URL = "https://app.fisify.com";
	private final String WEBVIEW_STAGING_URL = "https://staging-frontend-fisify.herokuapp.com";
	private final String WEBVIEW_LOCAL_URL = "http://192.168.2.195:3001";

	private final String NOTIFICATIONS_PRODUCTION_URL = "https://production-backend-fisify.herokuapp.com/api/devices";
	private final String NOTIFICATIONS_STAGING_URL = "https://staging-backend-fisify.herokuapp.com/api/devices";

	// find on google-service.json
	private final String PRODUCTION_CLIENT_ID = "602430523502-vdpv1vadcrd1em4a8nbo19hp4cdovgjn.apps.googleusercontent.com";
	private final String DEVELOPMENT_CLIENT_ID = "811151055222-b233lsqtl1bs7lssdc8103apr8bl9de4.apps.googleusercontent.com";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		initializeFirebaseAuthentication();

		context = getApplicationContext();
		window = getWindow();

		showSplashScreen();

		int MY_PERMISSIONS_REQUEST_CAMERA = 0;

		if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
		}

		startLoadingWebView();
		acceptBeforeUnloadAlertsAutomatically();
		showWebViewWhenLoaded();

		listenForNotificationRequestsFromJavascript();
	}

	protected void initializeFirebaseAuthentication() {
		GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
				.requestIdToken(PRODUCTION_CLIENT_ID)
				.requestEmail()
				.build();
		mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
	}

	@JavascriptInterface
	public void showGoogleSignIn () {
		Intent signInIntent = mGoogleSignInClient.getSignInIntent();
		startActivityForResult(signInIntent, RC_SIGN_IN);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == RC_SIGN_IN) {
			Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
			try {
				// Google Sign In was successful, authenticate with Firebase
				GoogleSignInAccount account = task.getResult(ApiException.class);
				String idToken = account.getIdToken();

				String javascriptCode = String.format("window.handleGoogleSignInFromAndroid('%s')", idToken);
				web.evaluateJavascript(javascriptCode,null);

				// https://stackoverflow.com/questions/38707133/google-firebase-sign-out-and-forget-user-in-android-app
				this.mGoogleSignInClient.signOut();
			} catch (ApiException e) {
				// Google Sign In failed, update UI appropriately
				Log.w(TAG, "Google sign in failed", e);
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	public void onBackPressed() {
		web.evaluateJavascript("location.href", value -> {
			if (value.endsWith("/login\"") || value.endsWith("/home\"") || value.endsWith("/catalog\"") || value.endsWith("/education\"") || value.endsWith("/profile\"")) {
				finish();
			} else {
				web.evaluateJavascript("history.back()",null);
			}
		});
	}

	private void showSplashScreen() {
		setContentView(R.layout.splash);
	}

	@SuppressLint("SetJavaScriptEnabled")
	private void startLoadingWebView() {
		WebView.setWebContentsDebuggingEnabled(true);

		web = new WebView(context);

		web.getSettings().setJavaScriptEnabled(true);
		web.getSettings().setDomStorageEnabled(true);
		web.getSettings().setAllowContentAccess(true);
		web.getSettings().setAllowFileAccess(true);
		web.getSettings().setMediaPlaybackRequiresUserGesture(false);

		final long systemMillis = System.currentTimeMillis() / 1000;
		final String timestamp = Long.toString(systemMillis);
		final String webviewUrl = WEBVIEW_PRODUCTION_URL + "?timestamp=" + timestamp;

		Log.d(TAG, webviewUrl);

		web.loadUrl(webviewUrl);
	}

	private void acceptBeforeUnloadAlertsAutomatically() {
		WebChromeClient webChromeClient = new WebChromeClient() {
			@Override
			public void onPermissionRequest(PermissionRequest request) {
				runOnUiThread(() -> {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						String[] PERMISSIONS = {
								PermissionRequest.RESOURCE_AUDIO_CAPTURE,
								PermissionRequest.RESOURCE_VIDEO_CAPTURE,
						};
						request.grant(PERMISSIONS);
					}
				});
			}
			@Override
			public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
				result.confirm();
				return super.onJsBeforeUnload(view, url, message, result);
			}
		};
		web.setWebChromeClient(webChromeClient);
	}

	private void showWebViewWhenLoaded() {
		Activity activity = this;

		web.setWebViewClient(new WebViewClient() {
			@Override
			public void onPageFinished(WebView view, String url) {
				final Handler timer = new Handler(Looper.getMainLooper());

				timer.postDelayed(() -> {
					activity.setContentView(web);
					window.setNavigationBarColor(getResources().getColor(R.color.fisifyBackground));
					window.setStatusBarColor(getResources().getColor(R.color.fisifyBackground));
				}, SPLASHSCREEN_DELAY_AFTER_PAGE_LOADED);
			}
		});
	}

	private void listenForNotificationRequestsFromJavascript() {
		web.addJavascriptInterface(this, "Android");
	}

	// https://nabeelj.medium.com/making-a-simple-get-and-post-request-using-volley-beginners-guide-ee608f10c0a9
	@JavascriptInterface
	public void sendDeviceAndroid(String uid) {
		FirebaseMessaging.getInstance().getToken()
				.addOnCompleteListener(task -> {
					if (!task.isSuccessful()) {
						Log.w(TAG, "Fetching FCM registration token failed", task.getException());
						return;
					}
					// Get new FCM registration token
					String token = task.getResult();
					Locale defaultLocale = Locale.getDefault();
					String locale = defaultLocale.getLanguage().substring(0, 2);
					RequestQueue queue = Volley.newRequestQueue(Main.this);

					JSONObject jsonBody = new JSONObject();
					try {
						jsonBody.put("firebaseUID", uid);
						jsonBody.put("token", token);
						jsonBody.put("lang", locale);
						jsonBody.put("platform", "android");
					} catch (JSONException e) {
						e.printStackTrace();
					}

					JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
							Request.Method.POST,
							NOTIFICATIONS_PRODUCTION_URL,
							jsonBody,
							response -> Log.d(TAG, response.toString()),
							error -> Log.e(TAG, "Known error because backend returns an empty JSON response.")
					);

					// https://stackoverflow.com/questions/27873001/android-volley-sending-data-twice
					jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(0, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

					// Add the request to the RequestQueue.
					queue.add(jsonObjectRequest);
				});
	}

	@JavascriptInterface
	public void fullScreen() {
		runOnUiThread(() -> {
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE);
		});
	}

	@JavascriptInterface
	public void normalScreen() {
		runOnUiThread(() -> {
			getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
		});
	}

	@JavascriptInterface
	public void landscape() {
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
	}

	@JavascriptInterface
	public void portrait() {
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
	}

	// https://stackoverflow.com/questions/42900826/how-can-i-show-an-image-from-link-in-android-push-notification
	public Bitmap getBitmapFromURL(String strURL) {
		try {
			URL url = new URL(strURL);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setDoInput(true);
			connection.connect();
			InputStream input = connection.getInputStream();
			Bitmap myBitmap = BitmapFactory.decodeStream(input);
			return myBitmap;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

}
