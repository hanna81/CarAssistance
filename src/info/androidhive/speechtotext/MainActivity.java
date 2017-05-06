package info.androidhive.speechtotext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Stack;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.language.Soundex;

import android.R.color;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private TextView txtSpeechInput;
	private ImageButton btnSpeak;
	private final int REQ_CODE_SPEECH_INPUT = 100;
	
	TextView listHeaderView;
	ListView matchesListv;
	
	TextToSpeech t1;
	
    List<String> supportedLanguages;
    String languagePreference;
	
	ArrayList<Contact> contactList;
	
	ArrayList<Contact> matches;
	Contact  callContact;
	String callNumber;
	
	List<Address> addressList;
	Address navigateAddress;
	
	Geocoder geoCoder;
	
	Soundex soundex;
	
	ArrayList<String> resultList;
	
	String language = "en";
	
	ACTION_STATE curr_state= ACTION_STATE.NONE;

	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		txtSpeechInput = (TextView) findViewById(R.id.txtSpeechInput);
		btnSpeak = (ImageButton) findViewById(R.id.btnSpeak);
		
		matchesListv= (ListView)findViewById(R.id.listView1);
		
		listHeaderView = new TextView(this);
		listHeaderView.setTextSize(30);
		listHeaderView.setTextColor(color.black);
		listHeaderView.setGravity(Gravity.CENTER);
		listHeaderView.setText("Choose From List");
		
		matchesListv.addHeaderView(listHeaderView);

		resultList = new ArrayList<>();
		
		Locale locale = new Locale(language);
		geoCoder = new Geocoder(this,locale);
		
		Intent detailsIntent =  new Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS);
		sendOrderedBroadcast( detailsIntent, null, new LanguageDetailsChecker(), null, Activity.RESULT_OK, null, null);
		
		
		t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
	         @Override
	         public void onInit(int status) {
	            if(status == TextToSpeech.SUCCESS) {	            	
	               int res = t1.setLanguage(Locale.US);             
	               if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED)
	               {
	            	   res = t1.setLanguage(Locale.UK);	            	
	               }
	               else
	               {
	            	   t1.setSpeechRate(1.0f);
	            	   String msg= "welcome to car assistance -- -- available commands are: "+
	            	   "call --"+" !! navigate -- or -- play music";
	            	   t1.speak(msg, TextToSpeech.QUEUE_FLUSH, null);	
	            	   txtSpeechInput.setText("Welcome to Car Assistance\nAvailable commands are: "+
	            	   "call, navigate and play music, you can say back any where to return to main menu");
	               }
	               
	            }
	         }
	      });
		
		soundex = new Soundex();
		
		initHebrewMap();
		
		String s1 = soundex.encode("Shalom");
		String s2 = soundex.encode("shlom");
		String s3 = soundex.encode(convertHebToEn("שלום"));
		
		s1 = soundex.encode("sireen");
		s2 = soundex.encode(convertHebToEn("סירין"));
		
		s1 = soundex.encode("joseph");
		s2 = soundex.encode("yosef");
		
		int diff =0;
		
		try {
			diff = soundex.difference("Shalom","salam");
			diff = soundex.difference("ٍsireen","sreen");
			diff = soundex.difference("ٍsireen","sheren");
			diff = soundex.difference("joseph", "josef");
			diff = soundex.difference("abu alshamshoum", "abu lshamshum");
		} catch (EncoderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		StateMenu main = new StateMenu();
		main.mState = ACTION_STATE.NONE;
		menuStack.push(main);
		
		// hide the action bar
		getActionBar().hide();
		
		getAllContacts();

		btnSpeak.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {								
					
				if (curr_state == ACTION_STATE.CALL_GET_NAME_DIGITS)
					promptSpeechInput(RECOGINTION_TYPE.CONTACT);
				else if (curr_state == ACTION_STATE.NAVIGATE_GET_PLACE)
					promptSpeechInput(RECOGINTION_TYPE.ADDRESS);
				else
					promptSpeechInput(RECOGINTION_TYPE.COMMAND);
			}
		});

		matchesListv.setAdapter(new BaseAdapter() {
			
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				// TODO Auto-generated method stub
						
				if (convertView == null)
				{
					TextView tv = new TextView(MainActivity.this);
					tv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
					tv.setTextSize(24);
					tv.setBackgroundColor(Color.BLUE);
					tv.setTextColor(Color.BLACK);
					tv.setGravity(Gravity.LEFT);
					String s = resultList.get(position);
					tv.setText(s);
					convertView = tv;
				}
				else
				{
					TextView tv = (TextView)convertView;
					String s = resultList.get(position);
					tv.setText(s);
					
				}
										
				return convertView;
			}
			
			@Override
			public long getItemId(int position) {
				// TODO Auto-generated method stub
				return 0;
			}
			
			@Override
			public Object getItem(int position) {
				// TODO Auto-generated method stub
				return resultList.get(position);
			}
			
			@Override
			public int getCount() {
				// TODO Auto-generated method stub
				return resultList.size();
			}
		});
		
		matchesListv.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// TODO Auto-generated method stub
				
				if (curr_state == ACTION_STATE.CALL_CHOOSE_CONTACT)
				{			
					callContact = matches.get(position-1);
					t1.speak("Calling "+ callContact.mDisplayName, TextToSpeech.QUEUE_FLUSH, null);
					Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + callContact.numbers.get(0)));			
					startActivity(intent);					
				}
				
				if (curr_state == ACTION_STATE.NAVIGATE_CHOOSE_PLACE)
				{
					navigateAddress = addressList.get(position);
					String uri = String.format("waze://?ll=%f,%f&navigate=yes",navigateAddress.getLatitude(),navigateAddress.getLongitude());
					startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri)));
				}
				
				curr_state = ACTION_STATE.NONE;
				resultList.clear();
				matchesListv.invalidateViews();
			}
			
		});	
	}

	enum  RECOGINTION_TYPE {
		COMMAND,CONTACT,ADDRESS,MUSIC
	}
	
	/**
	 * Showing google speech input dialog
	 * */
	private void promptSpeechInput(RECOGINTION_TYPE type) {
		Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); //  ACTION_VOICE_SEARCH_HANDS_FREE
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
	//	intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US);
	//	intent.putExtra(RecognizerIntent.EXTRA_PROMPT,getString(R.string.speech_prompt));
		
		if (type == RECOGINTION_TYPE.COMMAND)
		{
		//String myLanguage = "en"; //or, Locale.Italian.toString()
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language); 
		intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, language);
		}
		else
		{
			intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
			intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault()); 
		}
		
		try {
			startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
		} catch (ActivityNotFoundException a) {
			Toast.makeText(getApplicationContext(),
					getString(R.string.speech_not_supported),
					Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Receiving speech input
	 * */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
		case REQ_CODE_SPEECH_INPUT: {
			if (resultCode == RESULT_OK && null != data) {

				ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
				//txtSpeechInput.setText(result.get(0));
				
			//	ArrayList<String> confidence = data.getStringArrayListExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES);
				
				Toast.makeText(this, result.toString(), Toast.LENGTH_LONG).show();
				
				String text =result.get(0);
							
				switch (curr_state) {
				case NONE:
					
					text = convertToEnglish(text);
					
					int diff = 0;
					try {					 
						 diff = soundex.difference("call",text);
					} catch (EncoderException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					if (diff >=3 || result.contains("חייג") || result.contains("התקשר"))
					{
						curr_state = ACTION_STATE.CALL_GET_NAME_DIGITS;
						t1.speak("Please say contact name or number digit by digit", TextToSpeech.QUEUE_FLUSH, null);
						txtSpeechInput.setText("Please say contact name or number digit by digit");
						break;
					}
					
					if (result.contains("navigate") || result.contains("ניווט") || result.contains("נווט"))
					{
						curr_state = ACTION_STATE.NAVIGATE_GET_PLACE;
						t1.speak("Please say destination", TextToSpeech.QUEUE_FLUSH, null);
						txtSpeechInput.setText("Please say destination");
						break;
					}
					
					t1.speak("Sorry can't understand you",TextToSpeech.QUEUE_FLUSH, null);
					
					break;
				case  CALL_GET_NAME_DIGITS:
				{
					if (result.get(0).replaceAll(" ", "").matches("[0-9]+")) // digits only
					{
						callNumber = result.get(0).replaceAll(" ", "");
						t1.speak("Do you want to call number: -- "+callNumber+ " -- please say -- yes or -- no", TextToSpeech.QUEUE_FLUSH, null);
						txtSpeechInput.setText("Do you want to call number: "+callNumber+" please say yes or no");
						curr_state = ACTION_STATE.CALL_CONFIRM_CONTACT;
					}					
					else	
					{
						// find name in contacts and call	
						
						matches = new ArrayList<>();
						String name = result.get(0);
						
						String name_en = convertToEnglish(name);
							
						resultList.clear();
						
						for (Contact c:contactList)
						{
							//try find the whole name
							
							String cname= c.mDisplayName.replaceAll("\\p{Punct}", "");
							
							if (cname.equalsIgnoreCase(name))
							{
								matches.add(c);
								resultList.add(c.mDisplayName);
								continue;
							}
												
							
							String s1 = convertToEnglish(cname);
							
							try{
								if (soundex.difference(name_en, s1)>3)
								{
									matches.add(c);
									resultList.add(c.mDisplayName);
									continue;
								}	
							}catch (Exception e) {
								// TODO: handle exception
							}
							
							// if name is only one word
							if (name.split(" ").length ==1)
							{					
								String[] words = cname.split(" ");
								for (String w : words) 
								{
									try{
										String w1 = convertToEnglish(w);
										String name1 = convertToEnglish(name);
										
										if (soundex.difference(w1,name1) >3)
										{
											matches.add(c);
											resultList.add(c.mDisplayName);
										}	
									}catch(Exception ex){
										
									}
								}
							}
						}
						
						
						if (matches.size() ==0)
						{
							t1.speak("No contact matches found!", TextToSpeech.QUEUE_FLUSH, null);
							curr_state = ACTION_STATE.NONE;
							break;
						}
						
						showResults();
																			
						if (matches.size() ==1) // one match found
						{
							callContact = matches.get(0);
							String contact = convertToEnglish(callContact.mDisplayName);
							t1.speak("Do you want to call "+ contact + " -- Please say -- yes or -- no", TextToSpeech.QUEUE_FLUSH, null);
							txtSpeechInput.setText("Do you want to call "+ contact +" Please say Yes or No");
							curr_state = ACTION_STATE.CALL_CONFIRM_CONTACT;
						}
						
						if (matches.size()>1)
						{
							t1.speak(matches.size() + " matches found -- Please choose from list", TextToSpeech.QUEUE_FLUSH, null);
							txtSpeechInput.setText(matches.size() + " matches found -- Please choose from list");
							curr_state = ACTION_STATE.CALL_CHOOSE_CONTACT;												
						}				
						
					}
					break;
				}
				case CALL_CONFIRM_CONTACT:
				{
					if (result.contains("yes"))
					{
						String number ="";
						if (callContact !=null)
						{
							number = callContact.numbers.get(0);
							t1.speak("Calling "+ callContact.mDisplayName, TextToSpeech.QUEUE_FLUSH, null);
							txtSpeechInput.setText("Calling "+ callContact.mDisplayName);
						} 
						else if (callNumber != null)
						{
							number = callNumber;
							t1.speak("Calling number",TextToSpeech.QUEUE_FLUSH, null);
							txtSpeechInput.setText("Calling number");
						}							
						
						// call to number
						Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
						startActivity(intent);					
					}
					else
					{
						t1.speak("call aborted", TextToSpeech.QUEUE_FLUSH, null);
					}
					
					callContact = null;
					callNumber = null;
					
					resultList.clear();
					txtSpeechInput.setText("");
					
					curr_state = ACTION_STATE.NONE;
					
					break;
				}
				case NAVIGATE_GET_PLACE:
				{
					String dist = result.get(0);
					//search for place in google places
					searchForPlace(dist);
					
					resultList.clear();
				
					
					if (addressList == null || addressList.isEmpty())
					{
						t1.speak("No locations found!", TextToSpeech.QUEUE_FLUSH, null);
						curr_state = ACTION_STATE.NONE;
						break;
					}
												
					for (Address a :addressList)
					{
						resultList.add(a.getAddressLine(0)+", "+a.getLocality());													
					}
					
					showResults();
				
					if (addressList.size() == 1)
					{
						navigateAddress = addressList.get(0);
						String location = convertToEnglish(navigateAddress.getAddressLine(0)+","+ navigateAddress.getLocality());
						t1.speak("Please confirm navigating to  "+location+" -- by saying -- yes or -- no ",TextToSpeech.QUEUE_FLUSH, null);					
						curr_state = ACTION_STATE.NAVIGATE_CONFIRM;
					}
					
					if (addressList.size()>1)
					{											
						t1.speak(""+ matches.size() + " results found -- Please choose location from list:", TextToSpeech.QUEUE_FLUSH, null);
						curr_state = ACTION_STATE.NAVIGATE_CHOOSE_PLACE;
					}
									
					break;
				}
				
				case NAVIGATE_CONFIRM:
				{
					try{
						if (soundex.difference(text,"yes") >3)
						{
							t1.speak("Starting Navigation",TextToSpeech.QUEUE_FLUSH, null);
							
							String uri = String.format("waze://?ll=%f,%f&navigate=yes",navigateAddress.getLatitude(),navigateAddress.getLongitude());
							startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri)));
						}
						else
						{
							t1.speak("navigation aborted", TextToSpeech.QUEUE_FLUSH, null);
							
						}
					}catch(Exception ex){
						ex.printStackTrace();
					}
					
					resultList.clear();
					txtSpeechInput.setText("");
					
					navigateAddress =null;
					curr_state = ACTION_STATE.NONE;
					
					break;
				}
				
				default:
					break;
				}					
		}
			break;
		}

		}
	}
	

	void getAllContacts()
	{
		
		contactList = new ArrayList<>();
		
		ContentResolver cr = getContentResolver();
		Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI,
		        null, null, null, null);

		if (cur.getCount() > 0) {
		    while (cur.moveToNext()) {
		        String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
		        String name = cur.getString(cur.getColumnIndex( ContactsContract.Contacts.DISPLAY_NAME));

		        Contact c = new Contact(Integer.parseInt(id),name);
		        
		        if (cur.getInt(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
		            Cursor pCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,null,
		                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID +" = ?", new String[]{id}, null);
		            while (pCur.moveToNext()) {
		                String phoneNo = pCur.getString(pCur.getColumnIndex(
		                        ContactsContract.CommonDataKinds.Phone.NUMBER));
		                
		                c.numbers.add(phoneNo);
		             //   Toast.makeText(this, "Name: " + name 
		             //           + ", Phone No: " + phoneNo, Toast.LENGTH_SHORT).show();
		                
		            }	       
		            
		            pCur.close();		            
		            contactList.add(c);		            
		        }
		    }
		    
		}
	}
	
	
	List<Address> searchForPlace(String location)
	{		 
		addressList =null;
		
		try {
			addressList = geoCoder.getFromLocationName(location, 5);		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return addressList; 
	}
	
	
	HashMap<Character, String> hebrewDictionary = new HashMap<>();
	
	void initHebrewMap()
	{
		
		hebrewDictionary.put('א',"A");
		hebrewDictionary.put('ב',"b,v");
		hebrewDictionary.put('ג', "g");
		hebrewDictionary.put('ד', "d");
		hebrewDictionary.put('ה', "h,ah");
		hebrewDictionary.put('ו',"o,v");
		hebrewDictionary.put('ז',"z");
		hebrewDictionary.put('ח',"kh");
		hebrewDictionary.put('ט',"t");
		hebrewDictionary.put('י',"i,y");
		hebrewDictionary.put('כ',"k,kh");
		hebrewDictionary.put('ל',"le,l");
		hebrewDictionary.put('מ',"m");
		hebrewDictionary.put('ן',"n");
		hebrewDictionary.put('ס',"s");
		hebrewDictionary.put('ע', "a");
		hebrewDictionary.put('פ', "p,f");
		hebrewDictionary.put('צ',"tsa");
		hebrewDictionary.put('ק',"k");
		hebrewDictionary.put('ר',"r");
		hebrewDictionary.put('ש',"sh");
		hebrewDictionary.put('ת',"t");
		hebrewDictionary.put('ץ',"ts");
		hebrewDictionary.put('ך',"kh");
		hebrewDictionary.put('ף',"f");
		hebrewDictionary.put('ן',"n,en");
		hebrewDictionary.put('ם',"m");
	}
	
	
	String convertToEnglish(String text)
	{
		String engText= text;
		
		String hebAlphabet = "אבגדהוזחטיכלמןסעפצקרשתץךףן";
	//	String arabicAlphabet = "حخهعغفقثصضكمنتالبيسشزوةىﻻرؤءئ ح خ ه ع غ ف ق ث ص ض ك م ن ت ا ل ب ي س ش ز و ة ى ﻻ ر ؤ ء ئ";
						
		char c = text.charAt(0);
					
		if (hebAlphabet.indexOf(c)!= -1) // hebrew text
		{
			engText =convertHebToEn(text);			
		}
		
		return engText;
	}
	
	
	String convertHebToEn(String hebText)
	{
		String specialChars = "בכפ";
		String vouwels = "הויא";
		
		String s ="";
			
		for (int i=0;i<hebText.length();i++)
		{
			char c = hebText.charAt(i);
			
			if (!hebrewDictionary.containsKey(c))
			{
				s+=c;
				continue;
			}
				
			String []vals = hebrewDictionary.get(c).split(",");
			
			if (specialChars.indexOf(c) != -1)
			{
				
				if (i==0 || ("בחלם".indexOf(hebText.charAt(i-1))==-1 && vouwels.indexOf(hebText.charAt(i-1))==-1))
				{
					s+= vals[0];
				}
				else
				{
					s+= vals[1];
				}			
			}
			else if (vouwels.indexOf(c) !=-1)
			{
				if (c == 'ה')
				{
					if (i == hebText.length()-1)
						s+= vals[1];
					else
						s+=vals[0];
				}
				
				if (c == 'י')
				{
					if (i ==0 || (i<hebText.length()-1 && hebText.charAt(i+1)== c))
						s+= vals[1];
					else				
						s+=vals[0];
				}	
				
				if (c == 'ו')
				{
					if (i ==0 || (i<hebText.length()-1 && hebText.charAt(i+1)== c))
						s+= vals[1];
					else			
						s+=vals[0];
				}	
				
				if (c == 'א')
				{
					s+=vals[0];
				}
				
			}
			else if(c == 'ל')
			{
				if (i ==0)
				{
					s+=vals[0];
				}
				else
				{
					s+=vals[1];
				}				
			}
			else
			{
				s+=vals[0];
			}
		}
		
		return s;
	}
	
	String convertArabicToEn(char c)
	{
	
		return "";
	}
	
	
	
	void showResults()
	{
		
		matchesListv.invalidateViews();
			
		if (curr_state == ACTION_STATE.CALL_CHOOSE_CONTACT)
		{			
			listHeaderView.setText("Choose Contact From List");
		}
		
		if (curr_state == ACTION_STATE.NAVIGATE_CHOOSE_PLACE)
		{
			listHeaderView.setText("Choose Destination From List");
		}
		
	}
	
	void playBeep()
	{
		while (t1.isSpeaking())
		{
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);             
		toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP,150);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub		
		super.onResume();
	}
	
	public void onDestroy(){
	      if(t1 !=null){
	         t1.stop();
	         t1.shutdown();
	      }
	      super.onDestroy();
	   }
	
	
	enum ACTION_STATE {
		NONE, CALL_GET_NAME_DIGITS, CALL_CONFIRM_CONTACT, CALL_CHOOSE_CONTACT, CALLING,
		NAVIGATE_GET_PLACE, NAVIGATE_CHOOSE_PLACE,NAVIGATE_CONFIRM,NAVIGATING
	}
	
	class Contact{
		
		int mId;
		String mDisplayName;
		ArrayList<String> numbers;
		
		public Contact(int id,String name) {
			// TODO Auto-generated constructor stub
			mId = id;
			mDisplayName = name;
			numbers = new ArrayList<>();
		}
		
	}
	
	
	
	Stack<StateMenu>  menuStack = new Stack<>();
	
	class StateMenu 
	{
		ACTION_STATE  mState;
		String mTTsPrompt;
		String mTextMessage;
			
		ArrayList<String> resultList;
		ArrayList<Object> dataList;		
				
	}
	
	
	
	public class LanguageDetailsChecker extends BroadcastReceiver
	{
	
	    @Override
	    public void onReceive(Context context, Intent intent)
	    {
	        Bundle results = getResultExtras(true);
	        if (results.containsKey(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE))
	        {
	            languagePreference =
	                    results.getString(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE);
	        }
	        if (results.containsKey(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES))
	        {
	            supportedLanguages =
	                    results.getStringArrayList(
	                            RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES);
	            
	          //  t1.speak("supported languages are "+ supportedLanguages.toString(), TextToSpeech.QUEUE_ADD, null);
	        }
	    }
	}
	
}
