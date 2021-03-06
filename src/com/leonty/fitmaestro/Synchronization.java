package com.leonty.fitmaestro;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.leonty.fitmaesto.remote.ServerJson;
import com.leonty.fitmaestro.domain.FitmaestroDb;
import com.leonty.fitmaestro.domain.Synchro;

public class Synchronization {

	private final Context mCtx;
	DateFormat iso8601Format;
	
	public HashMap<String, String> groupFields = new HashMap<String, String>();
	public HashMap<String, String> filesFields = new HashMap<String, String>();
	public HashMap<String, String> exerciseFields = new HashMap<String, String>();
	public HashMap<String, String> logFields = new HashMap<String, String>();
	public HashMap<String, String> programFields = new HashMap<String, String>();
	public HashMap<String, String> programs_connectorFields = new HashMap<String, String>();
	public HashMap<String, String> sessionFields = new HashMap<String, String>();
	public HashMap<String, String> sessions_connectorFields = new HashMap<String, String>();
	public HashMap<String, String> sessions_detailFields = new HashMap<String, String>();

	public HashMap<String, String> setFields = new HashMap<String, String>();
	public HashMap<String, String> sets_connectorFields = new HashMap<String, String>();
	public HashMap<String, String> sets_detailFields = new HashMap<String, String>();

	public HashMap<String, String> measurement_typesFields = new HashMap<String, String>();
	public HashMap<String, String> measurements_logFields = new HashMap<String, String>();

	private FitmaestroDb db;
	private Synchro synchro;
	
	public Synchronization(Context ctx) {

		this.mCtx = ctx;

		db = new FitmaestroDb(ctx).open();
		synchro = new Synchro(db);			

		iso8601Format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		iso8601Format.setTimeZone(TimeZone.getTimeZone("UTC"));

		fillHashes();
	}

	public void fillHashes() {

		groupFields.put("title", FitmaestroDb.KEY_TITLE);
		groupFields.put("desc", FitmaestroDb.KEY_DESC);
		
		filesFields.put("filename", FitmaestroDb.KEY_FILENAME);
		
		exerciseFields.put("title", FitmaestroDb.KEY_TITLE);
		exerciseFields.put("desc", FitmaestroDb.KEY_DESC);
		exerciseFields.put("group_id", FitmaestroDb.KEY_GROUPID);
		exerciseFields.put("ex_type", FitmaestroDb.KEY_TYPE);
		exerciseFields.put("max_weight", FitmaestroDb.KEY_MAX_WEIGHT);
		exerciseFields.put("max_reps", FitmaestroDb.KEY_MAX_REPS);



		logFields.put("exercise_id", FitmaestroDb.KEY_EXERCISEID);
		logFields.put("weight", FitmaestroDb.KEY_WEIGHT);
		logFields.put("reps", FitmaestroDb.KEY_REPS);
		logFields.put("done", FitmaestroDb.KEY_DONE);
		logFields.put("session_id", FitmaestroDb.KEY_SESSIONID);
		logFields.put("sessions_detail_id",
				FitmaestroDb.KEY_SESSIONS_DETAILID);

		programFields.put("title", FitmaestroDb.KEY_TITLE);
		programFields.put("desc", FitmaestroDb.KEY_DESC);

		programs_connectorFields.put("program_id",
				FitmaestroDb.KEY_PROGRAMID);
		programs_connectorFields.put("set_id", FitmaestroDb.KEY_SETID);
		programs_connectorFields.put("day_number",
				FitmaestroDb.KEY_DAY_NUMBER);

		sessionFields.put("programs_connector_id",
				FitmaestroDb.KEY_PROGRAMS_CONNECTORID);
		sessionFields.put("title", FitmaestroDb.KEY_TITLE);
		sessionFields.put("desc", FitmaestroDb.KEY_DESC);
		sessionFields.put("status", FitmaestroDb.KEY_STATUS);

		sessions_connectorFields.put("session_id",
				FitmaestroDb.KEY_SESSIONID);
		sessions_connectorFields.put("exercise_id",
				FitmaestroDb.KEY_EXERCISEID);

		sessions_detailFields.put("sessions_connector_id",
				FitmaestroDb.KEY_SESSIONS_CONNECTORID);
		sessions_detailFields.put("reps", FitmaestroDb.KEY_REPS);
		sessions_detailFields.put("percentage",
				FitmaestroDb.KEY_PERCENTAGE);

		setFields.put("title", FitmaestroDb.KEY_TITLE);
		setFields.put("desc", FitmaestroDb.KEY_DESC);

		sets_connectorFields.put("set_id", FitmaestroDb.KEY_SETID);
		sets_connectorFields.put("exercise_id",
				FitmaestroDb.KEY_EXERCISEID);

		sets_detailFields.put("sets_connector_id",
				FitmaestroDb.KEY_SETS_CONNECTORID);
		sets_detailFields.put("reps", FitmaestroDb.KEY_REPS);
		sets_detailFields.put("percentage", FitmaestroDb.KEY_PERCENTAGE);

		measurement_typesFields.put("title", FitmaestroDb.KEY_TITLE);
		measurement_typesFields.put("units", FitmaestroDb.KEY_UNITS);
		measurement_typesFields.put("desc", FitmaestroDb.KEY_DESC);

		measurements_logFields.put("value", FitmaestroDb.KEY_VALUE);
		measurements_logFields.put("measurement_type_id",
				FitmaestroDb.KEY_MEASUREMENT_TYPEID);
		measurements_logFields.put("date", FitmaestroDb.KEY_DATE);

	}

	public int startSynchronization() throws JSONException {
		/*
		 * 1. Send to site new/updated items 2. Site performs updates and gives
		 * back his items but with relations not complete 3. Perform updates on
		 * the phone and give back id's 4. Site receives id's - repairs
		 * relations and gives back items with updated relations 5. we perform
		 * updates from p.3 and give nothing back
		 */
		String lastUpdated = String.valueOf(synchro.getLastUpdated());
		Log.i("Last updated", lastUpdated);
		String authKey = synchro.getAuthKey();
		ServerJson Js = new ServerJson();
		JSONObject jsonUpdateData = new JSONObject();
		JSONObject sendFirstData = prepareLocalUpdates(lastUpdated);

		// fresh means that data on the phone is clean - so we have to reset
		// phone id's on the server
		// so update will go properly
		long fresh = 0;

		// we have empty string - phone has never been updated before
		if (lastUpdated.equals("null")) {
			Log.i("FRESH INSTALL", "fresh");
			lastUpdated = "";
			fresh = 1;
		}
		jsonUpdateData = Js.getUpdates(authKey, sendFirstData, fresh);
		if (jsonUpdateData != null) {

			JSONObject jsonRelationsData = new JSONObject();
			jsonRelationsData = Js.finishUpdates(authKey,
					updateItems(jsonUpdateData));

			if (jsonRelationsData != null) {

				updateItems(jsonRelationsData);
				Log.i("Last updated", "Saving last updated!");
				synchro.setLastUpdated();
				
				//everything is good so we need to update files
				try {
					
					Log.i("UPDATING FILES", "updating...");
					updateFiles();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				Log.i("WTF2", "Second Fuck!");
			}
		} else {
			Log.i("WTF", "Fuck!");
			return ServerJson.NO_CONNECTION;
		}

		return ServerJson.SUCCESS;
	}

	public JSONObject updateItems(JSONObject jsonUpdateData)
			throws JSONException {

		// sending data back for phone_id updates
		JSONObject backData = new JSONObject();

		JSONArray groups = jsonUpdateData.getJSONArray("groups");
		JSONArray groupsReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_GROUPS_TABLE, groups, groupFields);
		backData.put("groups", groupsReturn);

		JSONArray files = jsonUpdateData.getJSONArray("files");
		JSONArray filesReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_FILES_TABLE, files, filesFields);
		backData.put("files", filesReturn);
		
		JSONArray exercises = jsonUpdateData.getJSONArray("exercises");
		JSONArray exercisesReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_EXERCISES_TABLE, exercises,
				exerciseFields);
		backData.put("exercises", exercisesReturn);

		JSONArray sets = jsonUpdateData.getJSONArray("sets");
		JSONArray setsReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_SETS_TABLE, sets, setFields);
		backData.put("sets", setsReturn);

		JSONArray sets_connector = jsonUpdateData
				.getJSONArray("sets_connector");
		JSONArray sets_connectorReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_SETS_CONNECTOR_TABLE,
				sets_connector, sets_connectorFields);
		backData.put("sets_connector", sets_connectorReturn);

		JSONArray sets_detail = jsonUpdateData.getJSONArray("sets_detail");
		JSONArray sets_detailReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_SETS_DETAIL_TABLE, sets_detail,
				sets_detailFields);
		backData.put("sets_detail", sets_detailReturn);

		JSONArray programs = jsonUpdateData.getJSONArray("programs");
		JSONArray programsReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_PROGRAMS_TABLE, programs,
				programFields);
		backData.put("programs", programsReturn);

		JSONArray programs_connector = jsonUpdateData
				.getJSONArray("programs_connector");
		JSONArray programs_connectorReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_PROGRAMS_CONNECTOR_TABLE,
				programs_connector, programs_connectorFields);
		backData.put("programs_connector", programs_connectorReturn);

		JSONArray sessions = jsonUpdateData.getJSONArray("sessions");
		JSONArray sessionsReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_SESSIONS_TABLE, sessions,
				sessionFields);
		backData.put("sessions", sessionsReturn);

		JSONArray sessions_connector = jsonUpdateData
				.getJSONArray("sessions_connector");
		JSONArray sessions_connectorReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_SESSIONS_CONNECTOR_TABLE,
				sessions_connector, sessions_connectorFields);
		backData.put("sessions_connector", sessions_connectorReturn);

		JSONArray sessions_detail = jsonUpdateData
				.getJSONArray("sessions_detail");
		JSONArray sessions_detailReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_SESSIONS_DETAIL_TABLE,
				sessions_detail, sessions_detailFields);
		backData.put("sessions_detail", sessions_detailReturn);

		JSONArray log = jsonUpdateData.getJSONArray("log");
		JSONArray logReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_LOG_TABLE, log, logFields);
		backData.put("log", logReturn);

		JSONArray measurement_types = jsonUpdateData
				.getJSONArray("measurement_types");
		JSONArray measurement_typesReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_MEASUREMENT_TYPES_TABLE,
				measurement_types, measurement_typesFields);
		backData.put("measurement_types", measurement_typesReturn);

		JSONArray measurements_log = jsonUpdateData
				.getJSONArray("measurements_log");
		JSONArray measurements_logReturn = performItemsUpdate(
				FitmaestroDb.DATABASE_MEASUREMENTS_LOG_TABLE,
				measurements_log, measurements_logFields);
		backData.put("measurements_log", measurements_logReturn);

		return backData;

	}

	public JSONArray performItemsUpdate(String table, JSONArray items,
			HashMap<String, String> fields) throws JSONException {

		JSONArray itemsReturn = new JSONArray();

		for (int i = 0; i < items.length(); i++) {
			JSONObject item = items.getJSONObject(i);

			String siteId = item.getString("id");
			long rowId = item.getLong("phone_id");
			String deleted = item.getString("deleted");

			HashMap<String, String> updateFields = new HashMap<String, String>();

			updateFields.put(FitmaestroDb.KEY_DELETED, deleted);
			updateFields.put(FitmaestroDb.KEY_SITEID, siteId);

			Set<Map.Entry<String, String>> set = fields.entrySet();

			for (Map.Entry<String, String> entry : set) {

				updateFields.put(entry.getValue(), item.getString(entry
						.getKey()));
			}

			// this entry exist only on web server, adding it
			if (rowId == 0) {

				// we do not want to doubled entries when connection is bad for
				// example
				// so we try to fetch it first
				Cursor itemCursor = synchro.fetchItemBySiteId(table, siteId);

				if (itemCursor.getCount() == 0) {

					long phoneId = synchro.createItem(table, updateFields);

					// now put phone id's back to update site database
					JSONObject itemReturn = new JSONObject();
					itemReturn.put("phone_id", phoneId);
					itemReturn.put("site_id", siteId);
					itemsReturn.put(itemReturn);
				}
				itemCursor.close();
			} else { // it's already on the phone, so we need to update it

				synchro.updateItem(table, updateFields, rowId);
			}
		}

		return itemsReturn;
	}

	public JSONObject prepareLocalUpdates(String updated) {

		JSONObject dataToSend = new JSONObject();

		try {

			dataToSend.put("groups", prepareItems(
					FitmaestroDb.DATABASE_GROUPS_TABLE, groupFields,
					updated));
			dataToSend.put("files", prepareItems(
					FitmaestroDb.DATABASE_FILES_TABLE, filesFields,
					updated));
			dataToSend.put("exercises", prepareItems(
					FitmaestroDb.DATABASE_EXERCISES_TABLE,
					exerciseFields, updated));
			dataToSend.put("sets",
					prepareItems(FitmaestroDb.DATABASE_SETS_TABLE,
							setFields, updated));
			dataToSend.put("sets_connector", prepareItems(
					FitmaestroDb.DATABASE_SETS_CONNECTOR_TABLE,
					sets_connectorFields, updated));
			dataToSend.put("sets_detail", prepareItems(
					FitmaestroDb.DATABASE_SETS_DETAIL_TABLE,
					sets_detailFields, updated));
			dataToSend.put("programs", prepareItems(
					FitmaestroDb.DATABASE_PROGRAMS_TABLE, programFields,
					updated));
			dataToSend.put("programs_connector", prepareItems(
					FitmaestroDb.DATABASE_PROGRAMS_CONNECTOR_TABLE,
					programs_connectorFields, updated));
			dataToSend.put("sessions", prepareItems(
					FitmaestroDb.DATABASE_SESSIONS_TABLE, sessionFields,
					updated));
			dataToSend.put("sessions_connector", prepareItems(
					FitmaestroDb.DATABASE_SESSIONS_CONNECTOR_TABLE,
					sessions_connectorFields, updated));
			dataToSend.put("sessions_detail", prepareItems(
					FitmaestroDb.DATABASE_SESSIONS_DETAIL_TABLE,
					sessions_detailFields, updated));
			dataToSend
					.put("log", prepareItems(
							FitmaestroDb.DATABASE_LOG_TABLE, logFields,
							updated));
			dataToSend.put("measurement_types", prepareItems(
					FitmaestroDb.DATABASE_MEASUREMENT_TYPES_TABLE,
					measurement_typesFields, updated));
			dataToSend.put("measurements_log", prepareItems(
					FitmaestroDb.DATABASE_MEASUREMENTS_LOG_TABLE,
					measurements_logFields, updated));

			dataToSend.put("localtime", synchro.getLocalTime());
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return dataToSend;
	}

	public JSONArray prepareItems(String table, HashMap<String, String> fields,
			String updated) throws JSONException {

		JSONArray itemsReturn = new JSONArray();
		Cursor updatedItems = synchro.fetchUpdatedItems(table, updated);

		updatedItems.moveToFirst();
		for (int i = 0; i < updatedItems.getCount(); i++) {
			JSONObject jsonRow = new JSONObject();

			// every table has those columns:
			String rowId = updatedItems.getString(updatedItems
					.getColumnIndex(FitmaestroDb.KEY_ROWID));
			jsonRow.put("id", rowId);
			String siteId = updatedItems.getString(updatedItems
					.getColumnIndex(FitmaestroDb.KEY_SITEID));
			jsonRow.put("site_id", siteId);
			String deleted = updatedItems.getString(updatedItems
					.getColumnIndex(FitmaestroDb.KEY_DELETED));
			jsonRow.put("deleted", deleted);
			String updatedSend = updatedItems.getString(updatedItems
					.getColumnIndex(FitmaestroDb.KEY_UPDATED));
			try {
				Date stamp = iso8601Format.parse(updatedSend);
				jsonRow.put("stamp", stamp.getTime() / 1000);
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// custom columns
			Set<Map.Entry<String, String>> set = fields.entrySet();

			for (Map.Entry<String, String> entry : set) {
				Log.i("GETTING", entry.getKey());
				jsonRow.put(entry.getKey(), updatedItems.getString(updatedItems
						.getColumnIndex(entry.getValue())));
			}

			itemsReturn.put(jsonRow);
			updatedItems.moveToNext();
		}

		updatedItems.close();
		return itemsReturn;
	}
	
	@SuppressLint("WorldReadableFiles") public void updateFiles() throws IOException{
		Cursor currentFiles = synchro.fetchCurrentFiles();
		currentFiles.moveToFirst();
		for (int i = 0; i < currentFiles.getCount(); i++) {
			String currentFile = currentFiles.getString(currentFiles
					.getColumnIndex(FitmaestroDb.KEY_FILENAME));
			
			Log.i("CURRENT FILE: ", currentFile);
			File localFile = new File(currentFile);

			// if file does not exist - download it
			if(!localFile.exists()){
				
				// downloading the file				
				String remoteFile = ServerJson.address + "files/" + currentFile;
				Log.i("DOWNLOADING FILE: ", remoteFile);
				
			    URL u = new URL(remoteFile);
			    HttpURLConnection c = (HttpURLConnection) 
			    u.openConnection();
			    c.setRequestMethod("GET");
			    c.setDoOutput(true);
			    c.connect();
			    
			    InputStream in = c.getInputStream();

                FileOutputStream f = mCtx.openFileOutput(currentFile,
                                                        Context.MODE_WORLD_READABLE);
			    
			    byte[] buffer = new byte[1024];
			    int len1 = 0;
			    while ( (len1 = in.read(buffer)) > 0 ) {
			         f.write(buffer,0, len1);
			    }
			    f.close();
			    in.close();
			}
			
			currentFiles.moveToNext();
		}
		
		// go through deleted files and remove them if they exist
		Cursor deletedFiles = synchro.fetchDeletedFiles();
		deletedFiles.moveToFirst();
		for (int i = 0; i < deletedFiles.getCount(); i++) {
			String deletedFile = deletedFiles.getString(deletedFiles
					.getColumnIndex(FitmaestroDb.KEY_FILENAME));
			
			Log.i("DELETED FILE: ", deletedFile);
			File localFile = new File(deletedFile);

			// if file exist - remove it
			if(localFile.exists()){
				
				// removing the file
				Log.i("REMOVNG FILE: ", deletedFile);
				mCtx.deleteFile(deletedFile);
			}
			
			currentFiles.moveToNext();
		}		
		
	}

}
