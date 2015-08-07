package frontlinesms.com.jiraattachmentuploader;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;


public class MainActivity extends ActionBarActivity {

    private final String TAG = this.getClass().getSimpleName();
    private final int RESULT_SETTINGS = 1;
    private final String JIRA_ATTACHMENT_ENDPOINT = "https://%s/rest/api/2/issue/%s/attachments";
    private static int REQUEST_LOAD_IMAGE = 1;
    private Button uploadButton;
    private ImageView imagePreview;
    private Button clickToChooseView;
    private EditText ticketNumberInput;
    private Uri fileUri;
    private ProgressBar uploadProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        setContentView(R.layout.activity_main);

        uploadButton = (Button) findViewById(R.id.uploadButton);
        imagePreview = (ImageView) findViewById(R.id.imagePreview);
        ticketNumberInput = (EditText) findViewById(R.id.ticketNumberInput);
        uploadProgressBar = (ProgressBar) findViewById(R.id.uploadProgressBar);

        clickToChooseView = (Button) findViewById(R.id.uploadTrigger);
        clickToChooseView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(
                        Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                );
                startActivityForResult(intent, REQUEST_LOAD_IMAGE);
            }
        });

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uploadAttachment(fileUri);
            }
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

    /**
     * Gets the actual location of an image path from the file system.
     *
     * @param contentURI
     * @return
     */
    private String getRealImagePathFromURI(Uri contentURI) {
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

        String url = String.format(JIRA_ATTACHMENT_ENDPOINT, prefs.getString("jira_url", ""), ticketNumberInput.getText());

        AsyncHttpClient client = new AsyncHttpClient();
        client.setBasicAuth(username, password);
        client.addHeader("X-Atlassian-Token", "nocheck");

        Log.i(TAG, "file location: " + fileUri.getPath());

        File myFile = new File(getRealImagePathFromURI(fileUri));
        RequestParams params = new RequestParams();
        try {
            params.put("file", myFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "could not find to upload");
        } catch (IOException e) {
            Log.e(TAG, "IO exception not find to upload");
        }

        client.post(url, params, new AsyncHttpResponseHandler() {
            @Override
            public void onStart() {
                uploadProgressBar.setVisibility(View.VISIBLE);
                uploadButton.setEnabled(false);
                Log.i(TAG, "starting upload");
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                Log.i(TAG, "successful upload");
                Toast.makeText(getApplicationContext(), "Image was successfully uploaded", Toast.LENGTH_LONG).show();
                uploadProgressBar.setVisibility(View.INVISIBLE);
                uploadButton.setEnabled(true);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                // called when request is retried
                uploadProgressBar.setVisibility(View.INVISIBLE);
                uploadButton.setEnabled(true);

                if (statusCode == 0 ){
                    Toast.makeText(getApplicationContext(), "internet connection reset, please try again", Toast.LENGTH_LONG).show();
                }
                else if (statusCode == 404) {
                    Toast.makeText(getApplicationContext(), "Ticket not available", Toast.LENGTH_LONG).show();
                }
                else {
                    Toast.makeText(getApplicationContext(), "Jira error, status: "+statusCode, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "response status: " + statusCode);
                    Log.e(TAG, "failed upload upload");
                }
            }
        });
    }
}
