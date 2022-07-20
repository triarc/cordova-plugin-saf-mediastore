package com.customautosys.saf_mediastore;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.FileUtils;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;

public class SafMediastore extends CordovaPlugin implements ValueCallback<String>{
	protected CallbackContext callbackContext;
	protected HashMap<String,String> saveFileData=new HashMap<>();
	protected CordovaInterface cordovaInterface;
	protected CordovaWebView cordovaWebView;

	public enum Action {
		selectFolder,
		selectFile,
		openFolder,
		openFile,
		readFile,
		writeFile,
		saveFile
	}

	@Override
	public void initialize(CordovaInterface cordovaInterface,CordovaWebView webView){
		this.cordovaInterface=cordovaInterface;
		this.cordovaWebView=cordovaWebView;
	}

	@Override
	public boolean execute(String action,JSONArray args,CallbackContext callbackContext)throws JSONException{
		Method method=null;
		try{
			method=this.getClass().getMethod(action,JSONArray.class,CallbackContext.class);
		}catch(Exception e){
			debugLog(e);
		}
		if(method==null||!Modifier.isPublic(method.getModifiers()))return false;
		try{
			return (Boolean)method.invoke(this,args,callbackContext);
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	public boolean selectFolder(JSONArray args,CallbackContext callbackContext)throws JSONException{
		Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		String initialFolder=null;
		try{
			initialFolder=args.getString(0);
		}catch(JSONException e){
			debugLog(e);
		}
		if(initialFolder!=null)intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,initialFolder);
		this.callbackContext=callbackContext;
		cordovaInterface.startActivityForResult(this,intent,Action.selectFolder.ordinal());
		return true;
	}

	public boolean selectFile(JSONArray args,CallbackContext callbackContext)throws JSONException{
		Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
		String initialFolder=null;
		try{
			initialFolder=args.getString(0);
		}catch(JSONException e){
			debugLog(e);
		}
		if(initialFolder!=null)intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,initialFolder);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		if(intent.resolveActivity(cordovaInterface.getContext().getPackageManager()) != null){
			this.callbackContext=callbackContext;
			cordovaInterface.startActivityForResult(this,Intent.createChooser(intent,"Select File"),Action.selectFile.ordinal());
		}else{
			return false;
		}
		return true;
	}

	public boolean openFolder(JSONArray args,CallbackContext callbackContext){
		try{
			Intent intent=new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.parse(args.getString(0)),DocumentsContract.Document.MIME_TYPE_DIR);
			this.callbackContext=callbackContext;
			cordovaInterface.startActivityForResult(this,Intent.createChooser(intent,"Open folder"),Action.openFolder.ordinal());
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	public boolean openFile(JSONArray args,CallbackContext callbackContext){
		try{
			String uri=args.getString(0);
			Intent intent=new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.parse(uri),MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.substring(uri.lastIndexOf('.')+1)));
			this.callbackContext=callbackContext;
			cordovaInterface.startActivityForResult(this,intent,Action.openFile.ordinal());
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	@RequiresApi(api=Build.VERSION_CODES.Q)
	public boolean readFile(JSONArray args,CallbackContext callbackContext){
		try(
			InputStream inputStream=cordovaInterface.getContext().getContentResolver().openInputStream(Uri.parse(args.getString(0)));
			ByteArrayOutputStream byteArrayOutputStream=new ByteArrayOutputStream()
		){
			FileUtils.copy(inputStream, byteArrayOutputStream);
			callbackContext.success(byteArrayOutputStream.toByteArray());
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	public boolean writeFile(JSONArray args,CallbackContext callbackContext){
		try{
			JSONObject params=args.getJSONObject(0);
			String filename=params.getString("filename");
			String mimeType=MimeTypeMap.getSingleton().getMimeTypeFromExtension(filename.substring(filename.lastIndexOf('.')+1));
			String folder=null;
			try{
				folder=params.getString("folder");
			}catch(Exception e){
				debugLog(e);
			}
			String subFolder="";
			try{
				subFolder=params.getString("subFolder");
			}catch(Exception e){
				debugLog(e);
			}
			Uri uri=null;
			if(folder!=null&&!folder.trim().equals("")){
				DocumentFile documentFile=DocumentFile.fromTreeUri(
					cordovaInterface.getContext(),
					Uri.parse(folder)
				);
				if(subFolder!=null){
					DocumentFile subFolderDocumentFile=null;
					for(DocumentFile subFile:documentFile.listFiles()){
						if(subFile.isDirectory()&&subFile.getName().equals(subFolder)){
							subFolderDocumentFile=subFile;
							break;
						}
					}
					documentFile=subFolderDocumentFile!=null?subFolderDocumentFile:documentFile.createDirectory(subFolder);
				}
				uri=documentFile.createFile(
					mimeType,
					filename
				).getUri();
			}else{
				ContentResolver contentResolver=cordovaInterface.getContext().getContentResolver();
				ContentValues contentValues=new ContentValues();
				contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME,filename);
				contentValues.put(MediaStore.MediaColumns.MIME_TYPE,mimeType);
				if(!subFolder.startsWith("/"))subFolder="/"+subFolder;
				contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+subFolder);
				uri=contentResolver.insert(MediaStore.Files.getContentUri("external"),contentValues);
			}
			try(OutputStream outputStream=cordovaInterface.getContext().getContentResolver().openOutputStream(uri)){
				outputStream.write(Base64.decode(params.getString("data"),Base64.DEFAULT));
			}
			callbackContext.success(uri.toString());
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	public boolean saveFile(JSONArray args,CallbackContext callbackContext){
		try{
			JSONObject params=args.getJSONObject(0);
			saveFileData.put(callbackContext.getCallbackId(),params.getString("data"));
			Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			String filename=null;
			try{
				filename=params.getString("filename");
			}catch(Exception e){
				debugLog(e);
			}
			if(filename!=null){
				intent.setType(MimeTypeMap.getSingleton().getMimeTypeFromExtension(filename.substring(filename.lastIndexOf('.')+1)));
				intent.putExtra(Intent.EXTRA_TITLE,filename);
			}
			String folder=null;
			try{
				folder=params.getString("folder");
			}catch(Exception e){
				debugLog(e);
			}
			if(folder!=null)intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,folder);
			this.callbackContext=callbackContext;
			cordovaInterface.startActivityForResult(this,intent,Action.saveFile.ordinal());
			return true;
		}catch(Exception e){
			callbackContext.error(debugLog(e));
			return false;
		}
	}

	@Override
	public void onActivityResult(int requestCode,int resultCode,Intent intent){
		if(callbackContext==null){
			debugLog("callbackContext==null in onActivityResult");
			return;
		}
		if(resultCode!=Activity.RESULT_OK){
			callbackContext.error(debugLog(intent.getDataString()));
			return;
		}
		if(requestCode<0||requestCode>=Action.values().length){
			callbackContext.error(debugLog("Invalid request code: "+requestCode));
			return;
		}
		switch(Action.values()[requestCode]){
			case selectFolder:
			case selectFile:
				callbackContext.success(intent.getDataString());
				break;
			case openFolder:
			case openFile:
				callbackContext.success();
				break;
			case saveFile:
				String data=saveFileData.remove(callbackContext.getCallbackId());
				if(data==null){
					callbackContext.error(debugLog("No saveFileData in onActivityResult"));
					break;
				}
				try(OutputStream outputStream=cordovaInterface.getContext().getContentResolver().openOutputStream(intent.getData())){
					outputStream.write(Base64.decode(data,Base64.DEFAULT));
					callbackContext.success(intent.getDataString());
				}catch(Exception e){
					callbackContext.error(debugLog(e));
				}
				break;
			default:
				callbackContext.error(debugLog("Invalid request code: "+Action.values()[requestCode].toString()));
		}
	}

	public String debugLog(Throwable throwable){
		try {
			StringWriter stringWriter = new StringWriter();
			PrintWriter printWriter = new PrintWriter(stringWriter);
			throwable.printStackTrace(printWriter);
			String stackTrace=stringWriter.toString();
			Log.d(throwable.getLocalizedMessage(),stackTrace,throwable);
			cordovaWebView.getEngine().evaluateJavascript(
				"console.log('" +stackTrace.replace(
					"'",
					"\\'"
				).replace(
					"\n",
					"\\n"
				).replace(
					"\t",
					"\\t"
				)+"');",
				this
			);
			printWriter.close();
			stringWriter.close();
			return stackTrace;
		}catch(Exception e){
			e.printStackTrace();
			return "";
		}
	}

	public String debugLog(String message){
		Log.d(getClass().getName(),message);
		cordovaWebView.getEngine().evaluateJavascript(
			"console.log('" +message.replace(
				"'",
				"\\'"
			).replace(
				"\n",
				"\\n"
			).replace(
				"\t",
				"\\t"
			)+"');",
			this
		);
		return message;
	}

	@Override
	public void onReceiveValue(String value){}
}