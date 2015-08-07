package frontlinesms.com.jiraattachmentuploader;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.FileAsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;


public class MainActivity extends ActionBarActivity {

    private final String TAG = this.getClass().getSimpleName();
    private final int RESULT_SETTINGS = 1;
    private static int REQUEST_LOAD_IMAGE = 1;
    private Button uploadButton;
    private ImageView imagePreview;
    private TextView clickToChooseView;
    private EditText ticketNumberInput;
    private Uri fileUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        setContentView(R.layout.activity_main);

        uploadButton = (Button) findViewById(R.id.uploadButton);
        imagePreview = (ImageView) findViewById(R.id.imagePreview);
        ticketNumberInput = (EditText) findViewById(R.id.ticketNumberInput);

        clickToChooseView = (TextView) findViewById(R.id.uploadTrigger);
        clickToChooseView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(
                        Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                );
                startActivityForResult(intent, REQUEST_LOAD_IMAGE);
            }

            ;
        });

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uploadAttachment(fileUri);
            }

            ;
        });

        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null && intent.getType().startsWith("image/")) {
            Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) {
                updateImagePreview(imageUri);
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivityForResult(i, RESULT_SETTINGS);
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESULT_SETTINGS) {
            Context context = getApplicationContext();
            //setting updated, do anything fancy if necessary.
        }

        if (requestCode == REQUEST_LOAD_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            updateImagePreview(imageUri);
        }
    }

    /**
     * update the image preview with an image for the user to view.
     *
     * @param uri {@link android.net.Uri} of the attachment to be displayed
     */
    private void updateImagePreview(Uri uri) {
        fileUri = uri;
        imagePreview.setImageURI(uri);
        uploadButton.setEnabled(true);
    }

    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    /**
     * uploads and attachment to jira.
     *
     * @param fileUri {@link android.net.Uri} of the attachment to be sent over to jira.
     */
    private void uploadAttachment(Uri fileUri) {
        Context context = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String username = prefs.getString("jira_username", "");
        String password = prefs.getString("jira_password", "");

        String url = String.format("https://%s/rest/api/2/issue/%s/attachments",
                prefs.getString("jira_url", ""), ticketNumberInput.getText());


        AsyncHttpClient client = new AsyncHttpClient();
        client.setBasicAuth(username, password);
        client.addHeader("X-Atlassian-Token", "nocheck");

        Log.i(TAG, "file location: "+fileUri.getPath());

        File myFile = new File(getRealPathFromURI(fileUri));
        RequestParams params = new RequestParams();
        try {
            params.put("file", myFile);
        } catch (FileNotFoundException e) {
           Log.e(TAG, "could not find to upload");
        }catch (IOException e) {
           Log.e(TAG, "IO exception not find to upload");
        }

        client.post(url, params, new AsyncHttpResponseHandler() {
            @Override
            public void onStart() {
                // called before request is started
                Log.i(TAG, "starting upload");
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                // called when response HTTP status is "200 OK"
                Log.i(TAG, "successful upload");

            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                // called when request is retried
                Log.e(TAG, "response status: "+statusCode);
                Log.e(TAG, "response body" + new String(responseBody));
                Log.e(TAG, "failed upload upload");
            }
        });
    }
}
