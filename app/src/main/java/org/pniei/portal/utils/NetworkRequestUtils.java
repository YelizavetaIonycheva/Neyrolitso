package org.pniei.portal.utils;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import org.pniei.portal.listener.SpoListenerManager;

public class NetworkRequestUtils {
    private static final String URL_IMS_ADDRESS = "message.";
    private static final String URL_LOGIN_ADDRESS = "login.";
    private static final String URL_SERVICES_ADDRESS = "services.";
    private static final String URL_INCOMING_MSG = "/api/getIncomingMessages.php";
    private static final String URL_LIST_OF_CONT = "/api/getListOfContacts.php";
    private static final String URL_SEND_MSG = "/api/sendMessage.php";
    private static final String URL_UPLOAD_FILE = "/api/uploadFile.php";
    private static final String URL_ADD_CONTACT = "/api/addContact.php";
    private static final String URL_CHANGE_CONTACT = "/api/changeContact.php";
    private static final String URL_DROP_CONTACT = "/api/dropContact.php";
    private static final String URL_GET_FIRMWARE = "/api/getFirmware.php";

    private static final String TAG = "NetworkRequestUtils";
    private static final int TIME_OUT = 20000;
    private static final String TWO_HYPHENS = "--";
    private static final String BOUNDARY = "----BOUNDARY";

    private static String response(String url, String jsonRequest, int timeout) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(url);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Accept", "*/*");
            c.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            c.setConnectTimeout(timeout);
            c.setReadTimeout(timeout);
            c.setDoInput(true);
            c.setDoOutput(true);

            DataOutputStream os = new DataOutputStream(c.getOutputStream());
            os.write(jsonRequest.getBytes());
            os.flush();
            os.close();

            int status = c.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                br.close();
                Log.i(TAG, "url:" + url + " req:" + jsonRequest + " resp:" + sb);
                return sb.toString();
            } else {
                Log.e(TAG, "url:" + url + " status:" + status);
            }
        } catch (IOException ex) {
            Log.e(TAG, "url:" + url + " req:" + jsonRequest + " resp:error send");
            ex.printStackTrace();
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        return null;
    }

    private static String upload(long idFile, String url, String jsonRequest, int timeout, String fileName, InputStream fileStream, IsRunThread isRun) {
        final int LEN_DATA_READ = 5116;
        int lenRead, sizeFile, sizeDataRead, procent, lastProcent, status;
        byte[] data = new byte[LEN_DATA_READ];
        double koef;
        String str1, str2;

        HttpURLConnection c = null;
        Log.d(TAG, "Upload file: " + fileName + ", send : " + jsonRequest);

        String mime = Utils.getMimeType(fileName);
        if (mime == null) {
            mime = "application/octet-stream";
        }

        StringBuilder sb = new StringBuilder();
        String crlf = "\r\n";
        try {
            sb.append(TWO_HYPHENS).append(BOUNDARY).append(crlf)
                    .append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                    .append(new String(fileName.getBytes("utf-8"), "iso-8859-1")).append("\"").append(crlf)
                    .append("Content-Type: ").append(mime).append(crlf)
                    .append("Content-Transfer-Encoding: binary").append(crlf).append(crlf);
        } catch (Exception e) {
            // ignore
        }
        str1 = sb.toString();
        sb.setLength(0);
        sb.append(crlf).append(TWO_HYPHENS).append(BOUNDARY).append(crlf)
                .append("Content-Disposition: form-data; name=\"params\"").append(crlf)
                .append(crlf)
                .append(jsonRequest).append(crlf)
                .append(TWO_HYPHENS)
                .append(BOUNDARY)
                .append(TWO_HYPHENS)
                .append(crlf);
        str2 = sb.toString();
        sb.setLength(0);

        try {
            URL u = new URL(url);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Accept", "*/*");
            c.setRequestProperty("Cache-Control", "no-cache");
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
            c.setConnectTimeout(timeout);
            c.setReadTimeout(timeout);
            c.setDoInput(true);
            c.setDoOutput(true);
            c.setChunkedStreamingMode(-1);
            c.connect();

            DataOutputStream os = new DataOutputStream(c.getOutputStream());
            os.writeBytes(str1);

            sizeFile = fileStream.available();
            koef = 100.0 / sizeFile;
            sizeDataRead = 0;
            lastProcent = -1;

            while ((lenRead = fileStream.read(data)) > 0 && isRun.isValue()) {
                os.write(data, 0, lenRead);
                os.flush();
                sizeDataRead += lenRead;
                procent = (int) (sizeDataRead * koef);
                if (lastProcent != procent) {
                    SpoListenerManager.callFileSendingStatus(idFile, procent);
                    lastProcent = procent;
                }
            }

            if (!isRun.isValue()) {
                os.close();
                return null;
            }

            os.writeBytes(str2);
            os.flush();
            os.close();

            status = c.getResponseCode();
            Log.d(TAG, url + " Response code " + status);
            if (status == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                br.close();
                Log.d(TAG, "url:" + url + " req:" + jsonRequest + " resp:" + sb);
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "url:" + url + " req:" + jsonRequest + " resp:error send");
            e.printStackTrace();
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception ex) {
                    // ignore
                }
            }
        }
        return null;
    }

    // API методы

    public static JSONArray getIncomingMessages(String id, String signature) {
        String url = "http://" + URL_IMS_ADDRESS + PrefsUtils.ins().getServerDomainName() + URL_INCOMING_MSG;
        try {
            JSONObject request = new JSONObject();
            request.put("id", id);
            request.put("signature", signature);
            String response = response(url, request.toString(), TIME_OUT);
            if (response != null) {
                JSONObject jsonResponse = new JSONObject(response);
                if (jsonResponse.has("messages")) {
                    return jsonResponse.getJSONArray("messages");
                }
            }
        } catch (JSONException je) {
            je.printStackTrace();
        }
        return null;
    }

    public static JSONObject getListOfContacts(String id, String signature) {
        String url = "http://" + URL_IMS_ADDRESS + PrefsUtils.ins().getServerDomainName() + URL_LIST_OF_CONT;
        try {
            JSONObject request = new JSONObject();
            request.put("id", id);
            request.put("signature", signature);
            String response = response(url, request.toString(), TIME_OUT);
            if (response == null) return null;
            return new JSONObject(response);
        } catch (JSONException je) {
            je.printStackTrace();
        }
        return null;
    }

    public static JSONObject addContact(String id, String signature, String idUser, String fullName, String sipNumber) {
        String url = "http://" + URL_IMS_ADDRESS + PrefsUtils.ins().getServerDomainName() + URL_ADD_CONTACT;
        try {
            JSONObject request = new JSONObject();
            request.put("id", id);
            request.put("signature", signature);
            request.put("id_contact", idUser);
            request.put("phone", sipNumber);
            request.put("name", fullName);
            String response = response(url, request.toString(), TIME_OUT);
            return new JSONObject(response);
        } catch (Exception je) {
            je.printStackTrace();
        }
        return null;
    }

    public static JSONObject changeContact(String id, String signature, String idUser, String fullName) {
        String url = "http://" + URL_IMS_ADDRESS + PrefsUtils.ins().getServerDomainName() + URL_CHANGE_CONTACT;
        try {
            JSONObject request = new JSONObject();
            request.put("id", id);
            request.put("signature", signature);
            request.put("id_contact", idUser);
            request.put("name", fullName);
            String response = response(url, request.toString(), TIME_OUT);
            if (response == null) return null;
            return new JSONObject(response);
        } catch (Exception je) {
            je.printStackTrace();
        }
        return null;
    }

    public static JSONObject dropContact(String id, String signature, String idUser) {
        String url = "http://" + URL_IMS_ADDRESS + PrefsUtils.ins().getServerDomainName() + URL_DROP_CONTACT;
        try {
            JSONObject request = new JSONObject();
            request.put("id", id);
            request.put("signature", signature);
            request.put("id_contact", idUser);
            String response = response(url, request.toString(), TIME_OUT);
            if (response == null) return null;
            return new JSONObject(response);
        } catch (JSONException je) {
            je.printStackTrace();
        }
        return null;
    }

    public static JSONObject sendTextMessage(String id, String signature, String text, List<String> idUsers, String[] idFiles) {
        String url = "http://" + URL_IMS_ADDRESS + PrefsUtils.ins().getServerDomainName() + URL_SEND_MSG;
        try {
            JSONObject request = new JSONObject();
            request.put("id", id);
            request.put("signature", signature);
            request.put("text", text);

            if (idFiles != null && idFiles.length > 0) {
                JSONArray jsonIdFiles = new JSONArray();
                for (String idFile : idFiles) {
                    jsonIdFiles.put(idFile);
                }
                request.put("idFile", jsonIdFiles);
            }

            JSONArray jsonIdUsers = new JSONArray();
            for (String idUser : idUsers) {
                jsonIdUsers.put(idUser);
            }
            request.put("receivers", jsonIdUsers);

            String response = response(url, request.toString(), TIME_OUT);
            if (response == null) return null;
            return new JSONObject(response);
        } catch (JSONException je) {
            je.printStackTrace();
        }
        return null;
    }

    public static JSONObject uploadFile(long idFile, String id, String signature, String fileName, InputStream fileStream, IsRunThread isRun) {
        String url = "http://" + URL_IMS_ADDRESS + PrefsUtils.ins().getServerDomainName() + URL_UPLOAD_FILE;
        Log.i(TAG, "url = " + url);
        try {
            JSONObject request = new JSONObject();
            request.put("id", id);
            request.put("signature", signature);
            String response = upload(idFile, url, request.toString(), TIME_OUT, fileName, fileStream, isRun);
            if (response == null) return null;
            return new JSONObject(response);
        } catch (JSONException je) {
            je.printStackTrace();
        }
        return null;
    }

    public static boolean downloadFile(long idFile, String urlDownload, OutputStream fileStream, IsRunThread isRun) {
        final int LEN_DATA_READ = 5136;
        int lenRead, needRead, alreadyRead, fileSize, lenDataReadAll;
        byte[] data = new byte[LEN_DATA_READ];
        double koef;
        int procent, lastProcent = -1;

        HttpURLConnection c = null;
        Log.d(TAG, "url = " + urlDownload);

        try {
            URL url = new URL(urlDownload);
            c = (HttpURLConnection) url.openConnection();
            c.setDoInput(true);
            c.connect();

            int status = c.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                fileSize = c.getContentLength();
                koef = 100.0 / fileSize;
                alreadyRead = 0;
                lenDataReadAll = 0;
                needRead = LEN_DATA_READ;

                DataInputStream is = new DataInputStream(c.getInputStream());
                while ((lenRead = is.read(data, alreadyRead, needRead)) > 0 && isRun.isValue()) {
                    if (lenRead < LEN_DATA_READ) {
                        if ((alreadyRead + lenRead) < LEN_DATA_READ) {
                            if ((lenDataReadAll + alreadyRead + lenRead) != fileSize) {
                                alreadyRead += lenRead;
                                needRead = LEN_DATA_READ - alreadyRead;
                                continue;
                            }
                        }
                    }
                    alreadyRead += lenRead;
                    fileStream.write(data, 0, alreadyRead);

                    procent = (int) (lenDataReadAll * koef);
                    if (lastProcent != procent) {
                        SpoListenerManager.callFileSendingStatus(idFile, procent);
                        lastProcent = procent;
                    }
                    lenDataReadAll += alreadyRead;
                    alreadyRead = 0;
                    needRead = LEN_DATA_READ;
                }
                is.close();
                return isRun.isValue();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        return false;
    }

    public static JSONObject checkUpdate(String id, String signature, String currentVersion) {
        String url = "http://" + URL_LOGIN_ADDRESS + PrefsUtils.ins().getServerDomainName() + URL_GET_FIRMWARE;
        try {
            JSONObject request = new JSONObject();
            request.put("id", id);
            request.put("signature", signature);
            request.put("version", currentVersion);
            String response = response(url, request.toString(), TIME_OUT);
            if (response == null) return null;
            return new JSONObject(response);
        } catch (JSONException je) {
            je.printStackTrace();
        }
        return null;
    }
}