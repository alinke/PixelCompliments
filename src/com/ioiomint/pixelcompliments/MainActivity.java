package com.ioiomint.pixelcompliments;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import ioio.lib.api.AnalogInput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import android.hardware.SensorManager;
import alt.android.os.CountDownTimer;

/**
 * Displays images from an SD card.
 */
@SuppressLint({ "ParserError", "NewApi" })
public class MainActivity extends IOIOActivity   {
   
	private ioio.lib.api.RgbLedMatrix.Matrix KIND;  //have to do it this way because there is a matrix library conflict
    private static final String TAG = "Pixel Compliments";	
  	private short[] frame_ = new short[512];
  	private short[] rgb_;
  	public static final Bitmap.Config FAST_BITMAP_CONFIG = Bitmap.Config.RGB_565;
  	private byte[] BitmapBytes;
  	private byte[] BitmayArray;
  	private InputStream BitmapInputStream;
  	private InputStream BitmapInputStreamBlank;
  	private ByteBuffer bBuffer;
  	private ShortBuffer sBuffer;
  	private SensorManager mSensorManager;
  	private int width_original;
  	private int height_original; 
  	private int i = 0;
  	private int deviceFound = 0;
  	private Handler mHandler;
  	private final String LOG_TAG = "PixelCompliments";
  	private SharedPreferences prefs;
	private String OKText;
	private Resources resources;
	private String app_ver;	
	private int matrix_model;
	private final String tag = "";	
	
	///********** Timers
	private ConnectTimer connectTimer; 
	private ProxImageTimer proxImageTimer;
	private PauseBetweenImagesDurationTimer pausebetweenimagesdurationTimer;
	private MatrixDelayTimer matrixdelaytimer;
	//****************
	
	private boolean scanAllPics;
	private String setupInstructionsString; 
	private String setupInstructionsStringTitle;	
	private boolean noSleep = false;
     private Display display;
     private int imageDisplayDuration;
     private int pauseBetweenImagesDuration;
     private int proximityPin_;
     private int proximityThresholdLower_;
     private int proximityThresholdUpper_;
     private TextView proxTextView_;
     private TextView firstTimeSetup1_;
     private ProgressDialog pDialog = null;
     private boolean showProx_;
     private int proxTriggeredFlag = 0;
     private ioio.lib.api.RgbLedMatrix matrix_;
     private int proxCounter = 1;
     private boolean debug_;
     private int appFirstRunDone = 0;
     private int startupDelay_ = 5;
     

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); //force only portrait mode        
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);
        display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        try
        {
            app_ver = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
        }
        catch (NameNotFoundException e)
        {
            Log.v(LOG_TAG, e.getMessage());
        }
        
        //******** preferences code
        resources = this.getResources();
        setPreferences();
        //***************************
        
        if (noSleep == true) {        	      	
        	this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //disables sleep mode
        }	
        
        proxTextView_ = (TextView)findViewById(R.id.proxTextView);
        firstTimeSetup1_ = (TextView)findViewById(R.id.firstTimeSetup1);
        
        connectTimer = new ConnectTimer(30000,5000); //pop up a message if it's not connected by this timer
 		connectTimer.start(); //this timer will pop up a message box if the device is not found
 		
 		proxImageTimer = new ProxImageTimer(imageDisplayDuration*1000,imageDisplayDuration*1000);   		
 		pausebetweenimagesdurationTimer = new PauseBetweenImagesDurationTimer(pauseBetweenImagesDuration*1000,pauseBetweenImagesDuration*1000); 
 		
 		matrixdelaytimer = new MatrixDelayTimer(startupDelay_*1000,startupDelay_*1000);
 		
 		setupInstructionsString = getResources().getString(R.string.setupInstructionsString);
        setupInstructionsStringTitle = getResources().getString(R.string.setupInstructionsStringTitle);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) 
    {
       MenuInflater inflater = getMenuInflater();
       inflater.inflate(R.menu.mainmenu, menu);
       return true;
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item)
    {
		
      if (item.getItemId() == R.id.menu_instructions) {
 	    	AlertDialog.Builder alert=new AlertDialog.Builder(this);
 	      	alert.setTitle(setupInstructionsStringTitle).setIcon(R.drawable.icon).setMessage(setupInstructionsString).setNeutralButton(OKText, null).show();
 	   }
    	
	  if (item.getItemId() == R.id.menu_about) {
		  
		    AlertDialog.Builder alert=new AlertDialog.Builder(this);
	      	alert.setTitle(getString(R.string.menu_about_title)).setIcon(R.drawable.icon).setMessage(getString(R.string.menu_about_summary) + "\n\n" + getString(R.string.versionString) + " " + app_ver).setNeutralButton(OKText, null).show();	
	   }
    	
    	if (item.getItemId() == R.id.menu_prefs)
       {
    		
    		
    		Intent intent = new Intent()
       				.setClass(this,
       						com.ioiomint.pixelcompliments.preferences.class);   
    				this.startActivityForResult(intent, 0);
       }
    	
       return true;
    }


@Override
    public void onActivityResult(int reqCode, int resCode, Intent data) //we'll go into a reset after this
    {
    	super.onActivityResult(reqCode, resCode, data);    	
    	setPreferences(); //very important to have this here, after the menu comes back this is called, we'll want to apply the new prefs without having to re-start the app
    	
    	if (reqCode == 0 || reqCode == 1) {
    		appFirstRunDone = 0;  //let's reset this flag so we've got the timer delay for the artifacts issue
    	}
    	
    } 
    
    private void setPreferences() //here is where we read the shared preferences into variables
    {
     SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);   
     noSleep = prefs.getBoolean("pref_noSleep", false);
     showProx_ = prefs.getBoolean("pref_showProxValue", true);
     debug_ = prefs.getBoolean("pref_debugMode", false);
     
     imageDisplayDuration = Integer.valueOf(prefs.getString(   
  	        resources.getString(R.string.pref_imageDisplayDuration),
  	        resources.getString(R.string.imageDisplayDurationDefault)));   
     
     pauseBetweenImagesDuration = Integer.valueOf(prefs.getString(   
  	        resources.getString(R.string.pref_pauseBetweenImagesDuration),
  	        resources.getString(R.string.pauseBetweenImagesDurationDefault)));  
     
     startupDelay_ = Integer.valueOf(prefs.getString(   
   	        resources.getString(R.string.pref_startupDelay),
   	        resources.getString(R.string.startupDelayDefault)));  
     
    proximityPin_ = Integer.valueOf(prefs.getString(   
   	        resources.getString(R.string.pref_proximityPin),
   	        resources.getString(R.string.proximityPinDefault)));   
     
     
    proximityThresholdLower_ = Integer.valueOf(prefs.getString(   
  	        resources.getString(R.string.pref_proxThresholdLower),
  	        resources.getString(R.string.proximityThresholdLowerDefault)));   
    
    proximityThresholdUpper_ = Integer.valueOf(prefs.getString(   
  	        resources.getString(R.string.pref_proxThresholdUpper),
  	        resources.getString(R.string.proximityThresholdUpperDefault)));   
     
     
     matrix_model = Integer.valueOf(prefs.getString(   //the selected RGB LED Matrix Type
    	        resources.getString(R.string.selected_matrix),
    	        resources.getString(R.string.matrix_default_value))); 
     
     
     switch (matrix_model) {  //get this from the preferences
     case 0:
    	 KIND = ioio.lib.api.RgbLedMatrix.Matrix.SEEEDSTUDIO_32x16;
    	 BitmapInputStream = getResources().openRawResource(R.raw.selectpic);
    	 break;
     case 1:
    	 KIND = ioio.lib.api.RgbLedMatrix.Matrix.ADAFRUIT_32x16;
    	 BitmapInputStream = getResources().openRawResource(R.raw.selectpic);
    	 break;
     case 2:
    	 KIND = ioio.lib.api.RgbLedMatrix.Matrix.SEEEDSTUDIO_32x32_NEW; //v1
    	 BitmapInputStream = getResources().openRawResource(R.raw.selectpic32);
    	 break;
     case 3:
    	 KIND = ioio.lib.api.RgbLedMatrix.Matrix.SEEEDSTUDIO_32x32; //v2
    	 BitmapInputStream = getResources().openRawResource(R.raw.selectpic32);
    	 break;
     default:	    		 
    	 KIND = ioio.lib.api.RgbLedMatrix.Matrix.SEEEDSTUDIO_32x32; //v2 as the default
    	 BitmapInputStream = getResources().openRawResource(R.raw.selectpic32);
     }
         
     frame_ = new short [KIND.width * KIND.height];
	 BitmapBytes = new byte[KIND.width * KIND.height *2]; //512 * 2 = 1024 or 1024 * 2 = 2048
 }
    
   private void loadProxDelayTimer () {
	   showPleaseWait("Just a Moment..");
	   matrixdelaytimer.start();
   }
    
    private void loadProxImage() throws ConnectionLostException {
    	
    	
 		switch (proxCounter) { 
        case 1:
        	BitmapInputStream = getResources().openRawResource(R.raw.a1); 		            	
        	break;
        case 2:
        	BitmapInputStream = getResources().openRawResource(R.raw.a2);  
            break;
        case 3:
        	BitmapInputStream = getResources().openRawResource(R.raw.a3);		
            break;
        case 4:
        	BitmapInputStream = getResources().openRawResource(R.raw.a4);		
            break;	                
        case 5:
        	BitmapInputStream = getResources().openRawResource(R.raw.a5);		
            break;    
        case 6:
        	 BitmapInputStream = getResources().openRawResource(R.raw.a6);			            	
        	break;
        case 7:
        	BitmapInputStream = getResources().openRawResource(R.raw.a7);		
            break;
        case 8:
        	BitmapInputStream = getResources().openRawResource(R.raw.a8);		
            break;
        case 9:
        	BitmapInputStream = getResources().openRawResource(R.raw.a9);		
            break;	                
        case 10:
        	BitmapInputStream = getResources().openRawResource(R.raw.a10);		
            break;    
        case 11:
        	 BitmapInputStream = getResources().openRawResource(R.raw.a11);			            	
        	break;
        case 12:
        	BitmapInputStream = getResources().openRawResource(R.raw.a12);		
            break;
        case 13:
        	BitmapInputStream = getResources().openRawResource(R.raw.a13);		
            break;
        case 14:
        	BitmapInputStream = getResources().openRawResource(R.raw.a14);		
            break;	                
        case 15:
        	BitmapInputStream = getResources().openRawResource(R.raw.a15);		
            break;    
        case 16:
        	 BitmapInputStream = getResources().openRawResource(R.raw.a16);			            	
        	break;
        case 17:
        	BitmapInputStream = getResources().openRawResource(R.raw.a17);		
            break;
        case 18:
        	BitmapInputStream = getResources().openRawResource(R.raw.a18);		
            break;
        case 19:
        	BitmapInputStream = getResources().openRawResource(R.raw.a19);		
            break;	                
        case 20:
        	BitmapInputStream = getResources().openRawResource(R.raw.a20);		
            break;    
        case 21:
        	 BitmapInputStream = getResources().openRawResource(R.raw.a21);			            	
        	break;
        case 22:
        	BitmapInputStream = getResources().openRawResource(R.raw.a22);	
        	proxCounter = 0; //reset it back
            break; 
  }	   
 		
 		 proxCounter++;
 		 BitmapInputStreamBlank = getResources().openRawResource(R.raw.blank32);
 		 loadBlankRGB565(); //load a blank screen first
 		 matrix_.frame(frame_);  //write the blank frame to the matrix
 		
    	 loadRGB565(); //now load the normal message
    	 matrix_.frame(frame_);  //write to the matrix  
    	// matrix_.frame(frame_);  //write to the matrix    	
    	// matrix_.frame(frame_);  //write to the matrix    	
       //  matrix_.frame(frame_);  //write to the matrix    	
    	// matrix_.frame(frame_);  //write to the matrix    	
    	// matrix_.frame(frame_);  //write to the matrix    	
    	// matrix_.frame(frame_);  //write to the matrix    	
    	// matrix_.frame(frame_);  //write to the matrix    	
    	
    	 appFirstRunDone = 1;
    	 proxImageTimer.start(); //now start the timer and then we'll clear it later
    	
    }
    
    public class MatrixDelayTimer extends CountDownTimer
  	{

  		public MatrixDelayTimer(long startTime, long interval)
  			{
  				super(startTime, interval);
  			}

  		@Override
  		public void onFinish()
  			{
  			
  			try {
				loadProxImage();
			} catch (ConnectionLostException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
  			
  			pDialog.dismiss();
	  		
  			}

  		@Override
  		public void onTick(long millisUntilFinished)				{
  			//not used
  		}
  	}      
      
    
   private void loadRGB565()  {
	   
		try {
   			int n = BitmapInputStream.read(BitmapBytes, 0, BitmapBytes.length); // reads
   																				// the
   																				// input
   																				// stream
   																				// into
   																				// a
   																				// byte
   																				// array
   			Arrays.fill(BitmapBytes, n, BitmapBytes.length, (byte) 0);
   		} catch (IOException e) {
   			e.printStackTrace();
   		}

   		int y = 0;
   		for (int i = 0; i < frame_.length; i++) {
   			frame_[i] = (short) (((short) BitmapBytes[y] & 0xFF) | (((short) BitmapBytes[y + 1] & 0xFF) << 8));
   			y = y + 2;
   		}
   }
   
   private void loadBlankRGB565()  {
	   
		try {
  			int n = BitmapInputStreamBlank.read(BitmapBytes, 0, BitmapBytes.length); // reads
  																				// the
  																				// input
  																				// stream
  																				// into
  																				// a
  																				// byte
  																				// array
  			Arrays.fill(BitmapBytes, n, BitmapBytes.length, (byte) 0);
  		} catch (IOException e) {
  			e.printStackTrace();
  		}

  		int y = 0;
  		for (int i = 0; i < frame_.length; i++) {
  			frame_[i] = (short) (((short) BitmapBytes[y] & 0xFF) | (((short) BitmapBytes[y + 1] & 0xFF) << 8));
  			y = y + 2;
  		}
  }
   
   public class ProxImageTimer extends CountDownTimer
  	{

  		public ProxImageTimer(long startTime, long interval)
  			{
  				super(startTime, interval);
  			}

  		@Override
  		public void onFinish()
  			{
  			
  			//now let's start the next timer because we don't want the next image to immediately play if the user is still standing in the prox range, there should be some pause
  			pausebetweenimagesdurationTimer.start();
  			proxImageTimer.cancel();
  			try {
				clearMatrixImage();
			} catch (ConnectionLostException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
  			//proxTriggeredFlag = 0; //don't set this just yet so we can get the pause
  				
  			}

  		@Override
  		public void onTick(long millisUntilFinished)				{
  			//not used
  		}
  	}
   
   public class PauseBetweenImagesDurationTimer extends CountDownTimer
  	{

  		public PauseBetweenImagesDurationTimer(long startTime, long interval)
  			{
  				super(startTime, interval);
  			}

  		@Override
  		public void onFinish()
  			{
  			pausebetweenimagesdurationTimer.cancel();  			
  			proxTriggeredFlag = 0; //set this back so we can detect again
  				
  			}

  		@Override
  		public void onTick(long millisUntilFinished)				{
  			//not used
  		}
  	}    
	
    
    public class ConnectTimer extends CountDownTimer
	{

		public ConnectTimer(long startTime, long interval)
			{
				super(startTime, interval);
			}

		@Override
		public void onFinish()
			{
				if (deviceFound == 0) {
					showNotFound (); 					
				}
				
			}

		@Override
		public void onTick(long millisUntilFinished)				{
			//not used
		}
	}
    
  
	
	private void showNotFound() {	
		AlertDialog.Builder alert=new AlertDialog.Builder(this);
		alert.setTitle(getResources().getString(R.string.notFoundString)).setIcon(R.drawable.icon).setMessage(getResources().getString(R.string.bluetoothPairingString)).setNeutralButton(getResources().getString(R.string.OKText), null).show();	
}
      
	
    
    class IOIOThread extends BaseIOIOLooper {
	  		//private ioio.lib.api.RgbLedMatrix matrix_;
	  		private AnalogInput prox_;
	  		float proxValue;
	
	  		@Override
	  		protected void setup() throws ConnectionLostException {
	  			prox_ = ioio_.openAnalogInput(proximityPin_);		
	  			matrix_ = ioio_.openRgbLedMatrix(KIND);
	  			deviceFound = 1; //if we went here, then we are connected over bluetooth or USB
	  			connectTimer.cancel(); //we can stop this since it was found
	  			
	  			if (debug_ == true) {  			
		  			showToast("Bluetooth Connected");
		  			//showToast("App Started Flag: " + appAlreadyStarted);
	  			}
	  		
	  		}
	
	  		@Override
	  		public void loop() throws ConnectionLostException {
	  		
	  		try {
				
	  			proxValue = prox_.read();
	  			proxValue = proxValue * 1000;	
	  			int proxInt = (int)proxValue;
	  			
	  			if (showProx_ == true) {
	  				setText(Integer.toString(proxInt));
	  			}
	  			
	  			if ((proxValue >= proximityThresholdLower_) && (proxValue <= proximityThresholdUpper_) && (proxTriggeredFlag == 0)) { //if we're in range
	  				proxTriggeredFlag = 1;
	  				
	  				if (appFirstRunDone == 1) {
	  					loadProxImage();
	  				}
	  				else {
	  					loadProxDelayTimer(); //it's the first time so we need an extra delay because of the artifacts issue
	  				}	
	  			}
	  			
	  			//matrix_.frame(frame_); //if you put this here, the app will crash and lock up, mainly on older/slower phones
					
					Thread.sleep(10);
				} catch (InterruptedException e) {
					ioio_.disconnect();
				} catch (ConnectionLostException e) {
					throw e;
				}
	  		
	  			//matrix_.frame(frame_); //if you put this here, the app will crash and lock up
			}
	  		
	  		@Override
			public void disconnected() {
				Log.i(LOG_TAG, "IOIO disconnected");
				if (debug_ == true) {  			
		  			showToast("Bluetooth Disconnected");
	  			}
				 //ioio_.disconnect();  //that caused a fatal crash
			}
	
			@Override
			public void incompatible() {  //if the wrong firmware is there
				//AlertDialog.Builder alert=new AlertDialog.Builder(context); //causing a crash
				//alert.setTitle(getResources().getString(R.string.notFoundString)).setIcon(R.drawable.icon).setMessage(getResources().getString(R.string.bluetoothPairingString)).setNeutralButton(getResources().getString(R.string.OKText), null).show();	
				showToast("Incompatbile firmware!");
				showToast("This app won't work until you flash the IOIO with the correct firmware!");
				showToast("You can use the IOIO Manager Android app to flash the correct firmware");
				Log.e(LOG_TAG, "Incompatbile firmware!");
			}
  	}

  	@Override
  	protected IOIOLooper createIOIOLooper() {
  		return new IOIOThread();
  	}
    
    private void showToast(final String msg) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast toast = Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG);
                toast.show();
			}
		});
	}  
    
    private void showToastShort(final String msg) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast toast = Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT);
                toast.show();
			}
		});
	}  
    
    private void setHomeText(final String str) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				firstTimeSetup1_.setText(str);
			}
		});
	}
    
    private void setText(final String str) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				proxTextView_.setText(str);
			}
		});
	}
    
    private void showPleaseWait(final String str) {
		runOnUiThread(new Runnable() {
			public void run() {
				pDialog = ProgressDialog.show(MainActivity.this,"Please wait", str, true);
				pDialog.setCancelable(true);
			}
		});
	}
    
    private void screenOn() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				WindowManager.LayoutParams lp = getWindow().getAttributes();  //turn the screen back on
				lp.screenBrightness = 10 / 100.0f;  
				//lp.screenBrightness = 100 / 100.0f;  
				getWindow().setAttributes(lp);
			}
		});
	}
    
    private void clearMatrixImage() throws ConnectionLostException {
    	//let's claear the image
    	 BitmapInputStream = getResources().openRawResource(R.raw.blank32); //load a blank image to clear it
    	 loadRGB565();
    	 matrix_.frame(frame_);  //dont' forget to write the frame    	
    	 matrix_.frame(frame_); 
    	 matrix_.frame(frame_); 
    	 matrix_.frame(frame_); 
    	 matrix_.frame(frame_); 
    	 matrix_.frame(frame_); 
    	 matrix_.frame(frame_); 
    	 matrix_.frame(frame_); 
    	
    	//let's clear it and it will show again after the user triggers the next prox sensor
    }
    
   
    @Override
	protected void onPause() {  //note on pause you can clear the matrix before exiting, on stop and on destroy are too late
		super.onPause();
		try {
			clearMatrixImage();
		} catch (ConnectionLostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
   
    
}
    
    
    
    
    




























