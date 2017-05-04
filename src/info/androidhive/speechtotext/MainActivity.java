package info.androidhive.speechtotext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.language.Soundex;

import android.R.color;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
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
	
	public TextToSpeech t1;
	
	ArrayList<Contact> contactList;
	
	ArrayList<Contact> matches;
	Contact  callContact;
	
	List<Address> addressList;
	Address navigateAddress;
	
	Geocoder geoCoder;
	
	Soundex soundex;
	
	ArrayList<String> resultList;
	
	ACTION_STATE curr_state= ACTION_STATE.NONE;

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
		
		matchesListv.addHeaderView(listHeaderView);
		
		resultList = new ArrayList<>();
			
		geoCoder = new Geocoder(this,Locale.getDefault());
		
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
		
		// hide the action bar
		getActionBar().hide();
		
		getAllContacts();

		btnSpeak.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				promptSpeechInput();
				playBeep();
			}
		});

		matchesListv.setAdapter(new BaseAdapter() {
			
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				// TODO Auto-generated method stub
				
				if (convertView == null)
				{
					TextView tv = new TextView(MainActivity.this);
					tv.setTextSize(20);
					tv.setTextColor(color.black);
					convertView = tv;
				}
				
				TextView tv = (TextView)convertView;
				tv.setText(getItem(position).toString());
										
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
					callContact = matches.get(position);
					Intent callIntent = new Intent(Intent.ACTION_CALL);
					callIntent.putExtra(Intent.EXTRA_PHONE_NUMBER, callContact.numbers.get(0));
					startActivity(callIntent);
				}
				
				if (curr_state == ACTION_STATE.NAVIGATE_CHOOSE_PLACE)
				{
					navigateAddress = addressList.get(position);
					String uri = String.format("waze://?ll=%f,%f&navigate=yes",navigateAddress.getLatitude(),navigateAddress.getLongitude());
					startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri)));
				}
			}
			
		});	
	}

	/**
	 * Showing google speech input dialog
	 * */
	private void promptSpeechInput() {
		Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); //  ACTION_VOICE_SEARCH_HANDS_FREE
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
				RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US);
		intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
				getString(R.string.speech_prompt));
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
				txtSpeechInput.setText(result.get(0));
				
			//	ArrayList<String> confidence = data.getStringArrayListExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES);
				
				Toast.makeText(this, result.toString(), Toast.LENGTH_LONG).show();
				
				String text =result.get(0);
				
				
				switch (curr_state) {
				case NONE:
					if (result.contains("call") || result.contains("calling") || result.contains("חייג") || result.contains("התקשר"))
					{
						curr_state = ACTION_STATE.CALL_GET_NAME_DIGITS;
						t1.speak("Please say contact name or number digit by digit", TextToSpeech.QUEUE_FLUSH, null);
					}
					
					if (result.contains("navigate") || result.contains("ניווט") || result.contains("נווט"))
					{
						curr_state = ACTION_STATE.NAVIGATE_GET_PLACE;
						t1.speak("Please say where", TextToSpeech.QUEUE_FLUSH, null);
					}
					break;
				case  CALL_GET_NAME_DIGITS:
				{
					if (result.get(0).matches("[+-0123456789]")) // digits only
					{
						t1.speak("Please confirm calling number: "+result.get(0)+ " by saying yes or no "+matches.get(0).mDisplayName, TextToSpeech.QUEUE_FLUSH, null);
					}					
					else	
					{
						// find name in contacts and call	
						
						matches = new ArrayList<>();
						String name = result.get(0);
							
						resultList.clear();
						
						for (Contact c:contactList)
						{
							String[] words = c.mDisplayName.split(" ");
							for (String w : words) 
							{
								try{
									String w1 = convertToEnglish(w);
									String name1 = convertToEnglish(name);
									
									if (soundex.difference(w1,name1) >=3)
									{
										matches.add(c);
										resultList.add(c.mDisplayName);
									}	
								}catch(Exception ex){
									
								}
							}
						}
						
						if (matches.size() ==1) // one match found
						{
							callContact = matches.get(0);
							t1.speak("Please confirm calling "+ callContact.mDisplayName+ " by saying yes or no "+matches.get(0).mDisplayName, TextToSpeech.QUEUE_FLUSH, null);
							curr_state = ACTION_STATE.CALL_CONFIRM_CONTACT;
						}
						
						if (matches.size()>1)
						{
							t1.speak(""+ matches.size() + " matches found /s Please choose from list:", TextToSpeech.QUEUE_FLUSH, null);
							curr_state = ACTION_STATE.CALL_CHOOSE_CONTACT;
													
						}	
						
						if (matches.size() ==0)
						{
							t1.speak("No contact matches found!", TextToSpeech.QUEUE_FLUSH, null);
							curr_state = ACTION_STATE.NONE;
						}
						
						showResults(resultList);
					}
					break;
				}
				case CALL_CONFIRM_CONTACT:
				{
					if (result.contains("yes"))
					{
						t1.speak("Calling "+ callContact.mDisplayName, TextToSpeech.QUEUE_FLUSH, null);
						// call to number
						Intent callIntent = new Intent(Intent.ACTION_CALL);
						callIntent.putExtra(Intent.EXTRA_PHONE_NUMBER, callContact.numbers.get(0));
						startActivity(callIntent);					
					}
					else
					{
						t1.speak("call aborted", TextToSpeech.QUEUE_FLUSH, null);
					}
					
					curr_state = ACTION_STATE.NONE;
					
					break;
				}
				case NAVIGATE_GET_PLACE:
				{
					String dist = result.toString().replaceAll(",", "");
					//search for place in google places
					searchForPlace(dist);
					
					resultList.clear();
					
					if (addressList !=null && addressList.size()>0)
					{					
						for (Address a :addressList)
							resultList.add(a.getAddressLine(0)+", "+a.getLocality());
												
					}
								
					showResults(resultList);
					
					if (addressList == null || addressList.isEmpty())
					{
						t1.speak("No location matches found!", TextToSpeech.QUEUE_FLUSH, null);
						curr_state = ACTION_STATE.NONE;
					}
					
					if (addressList.size() == 1)
					{
						navigateAddress = addressList.get(0);
						t1.speak("Please confirm navigating to  "+ navigateAddress.getAddressLine(0)+ " by saying yes or no "+matches.get(0).mDisplayName, TextToSpeech.QUEUE_FLUSH, null);
						curr_state = ACTION_STATE.NAVIGATE_CONFIRM;
					}
					
					if (addressList.size()>1)
					{
						t1.speak(""+ matches.size() + " results found /s Please choose from list:", TextToSpeech.QUEUE_FLUSH, null);
						curr_state = ACTION_STATE.NAVIGATE_CHOOSE_PLACE;
					}
									
					break;
				}
				
				case NAVIGATE_CONFIRM:
				{
					if (result.contains("yes"))
					{
						t1.speak("Starting Navigation",TextToSpeech.QUEUE_FLUSH, null);					
					}
					else
					{
						t1.speak("navigation aborted", TextToSpeech.QUEUE_FLUSH, null);
					}
					
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
		hebrewDictionary.put('ב',"be,v");
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
			
			String []vals = hebrewDictionary.get(c).split(",");
			
			if (specialChars.indexOf(c) != -1)
			{
				
				if (i==0 || "בחלם".indexOf(hebText.charAt(i-1))==-1)
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
	
	
	
	void showResults(ArrayList<String> results)
	{
		
		resultList = results;
		matchesListv.invalidateViews();
		
		if (curr_state == ACTION_STATE.CALL_CHOOSE_CONTACT)
		{			
			listHeaderView.setText("Choose Contact");
		}
		
		if (curr_state == ACTION_STATE.NAVIGATE_CHOOSE_PLACE)
		{
			listHeaderView.setText("Choose Destination");
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
	            	   t1.speak("Ready", TextToSpeech.QUEUE_FLUSH, null);	            	   
	               }
	               
	            }
	         }
	      });
		
		super.onResume();
	}
	
	public void onPause(){
	      if(t1 !=null){
	         t1.stop();
	         t1.shutdown();
	      }
	      super.onPause();
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
	
	
}
