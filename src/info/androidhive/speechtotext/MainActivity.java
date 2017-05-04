package info.androidhive.speechtotext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
		Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
				RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
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

				ArrayList<String> result = data
						.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
				txtSpeechInput.setText(result.get(0));
				
				
				Toast.makeText(this, result.toString(), Toast.LENGTH_LONG).show();
				
				String cmd =result.get(0);
				
				
				switch (curr_state) {
				case NONE:
					if (result.contains("call") || result.contains("חייג"))
					{
						curr_state = ACTION_STATE.CALL_GET_NAME_DIGITS;
						t1.speak("Please say contact name or number digit by digit", TextToSpeech.QUEUE_FLUSH, null);
					}
					
					if (result.contains("navigate") || result.contains("ניווט"))
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
						String name = result.toString().replaceAll("[,.:;<>|{}=-_\"`!+-*&^%$#@~()]", "");
							
						resultList.clear();
						
						for (Contact c:contactList)
						{
							if (c.mDisplayName.toLowerCase().contains(name)){
								matches.add(c);
								resultList.add(c.mDisplayName);
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
					if (result.get(0).equalsIgnoreCase("yes"))
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
					if (result.get(0).equalsIgnoreCase("yes"))
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
	            if(status != TextToSpeech.ERROR) {
	               t1.setLanguage(Locale.UK);
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
