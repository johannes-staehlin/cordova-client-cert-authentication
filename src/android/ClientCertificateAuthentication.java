package de.jstd.cordova.plugin;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.util.Log;
import android.widget.Toast;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.ICordovaClientCertRequest;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ClientCertificateAuthentication extends CordovaPlugin {

    private static final String CRYPTO_RSA = "RSA";
    private static final String SP_KEY_ALIAS = "SP_KEY_ALIAS";
    private static final String TAG = "client-cert-auth";

    X509Certificate[] mCertificates;
    PrivateKey mPrivateKey;
    String mAlias;

    @Override
    public Boolean shouldAllowBridgeAccess(String url) {
        return super.shouldAllowBridgeAccess(url);
    }

    @Override
    public boolean onReceivedClientCertRequest(CordovaWebView view, ICordovaClientCertRequest request) {
        if (mCertificates == null || mPrivateKey == null) {
            choosePrivateKey(request);
        } else {
            proceedWithRequest(request);
        }
        return true;
    }

    private void choosePrivateKey(ICordovaClientCertRequest request) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(cordova.getActivity());
        final KeyChainAliasCallback callback = new PrivateKeyAliasCallback(cordova.getActivity(), request);
        final String alias = sp.getString(SP_KEY_ALIAS, null);

        if (alias == null) {
            KeyChain.choosePrivateKeyAlias(
                cordova.getActivity(), callback, new String[]{CRYPTO_RSA}, null, request.getHost(), request.getPort(), null);
        } else {
            ExecutorService threadPool = cordova.getThreadPool();
            threadPool.submit(new Runnable() {
                @Override
                public void run() {
                    callback.alias(alias);
                }
            });
        }
    }

    static class PrivateKeyAliasCallback implements KeyChainAliasCallback {

        private final SharedPreferences mPreferences;
        private final ICordovaClientCertRequest mRequest;
        private final Context mContext;

        public PrivateKeyAliasCallback(Context context, ICordovaClientCertRequest request) {
            mRequest = request;
            mContext = context;
            mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        }

        @Override
        public void alias(String alias) {
            try {
                if (alias != null) {
                    SharedPreferences.Editor edt = mPreferences.edit();
                    edt.putString(SP_KEY_ALIAS, alias);
                    edt.apply();
                    PrivateKey pk = KeyChain.getPrivateKey(mContext, alias);
                    X509Certificate[] cert = KeyChain.getCertificateChain(mContext, alias);
                    mRequest.proceed(pk, cert);
                } else {
                    mRequest.proceed(null, null);
                }
            } catch (KeyChainException e) {
                String errorText = "Failed to load certificates";
                Toast.makeText(mContext, errorText, Toast.LENGTH_SHORT).show();
                Log.e(TAG, errorText, e);
            } catch (InterruptedException e) {
                String errorText = "InterruptedException while loading certificates";
                Toast.makeText(mContext, errorText, Toast.LENGTH_SHORT).show();
                Log.e(TAG, errorText, e);
            }
        }
    }

    private void proceedWithRequest(ICordovaClientCertRequest request) {
        request.proceed(mPrivateKey, mCertificates);
    }
}
