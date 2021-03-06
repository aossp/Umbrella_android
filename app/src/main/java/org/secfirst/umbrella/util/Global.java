package org.secfirst.umbrella.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.StrictMode;
import android.support.multidex.MultiDex;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.downloader.Error;
import com.downloader.OnDownloadListener;
import com.downloader.PRDownloader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.ForeignCollection;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.table.TableUtils;
import com.loopj.android.http.JsonHttpResponseHandler;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONObject;
import org.secfirst.umbrella.BuildConfig;
import org.secfirst.umbrella.LoginActivity;
import org.secfirst.umbrella.R;
import org.secfirst.umbrella.RefreshService;
import org.secfirst.umbrella.TourActivity;
import org.secfirst.umbrella.fragments.SettingsFragment;
import org.secfirst.umbrella.models.Category;
import org.secfirst.umbrella.models.CheckItem;
import org.secfirst.umbrella.models.Difficulty;
import org.secfirst.umbrella.models.Favourite;
import org.secfirst.umbrella.models.FeedItem;
import org.secfirst.umbrella.models.FeedSource;
import org.secfirst.umbrella.models.Form;
import org.secfirst.umbrella.models.FormItem;
import org.secfirst.umbrella.models.FormOption;
import org.secfirst.umbrella.models.FormScreen;
import org.secfirst.umbrella.models.FormValue;
import org.secfirst.umbrella.models.Language;
import org.secfirst.umbrella.models.NewCategory;
import org.secfirst.umbrella.models.NewDifficulty;
import org.secfirst.umbrella.models.Registry;
import org.secfirst.umbrella.models.Segment;
import org.secfirst.umbrella.models.Tree;
import org.secfirst.umbrella.rss.entities.CustomFeed;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.fabric.sdk.android.Fabric;
import timber.log.Timber;

public class Global extends Application {

    private SharedPreferences prefs;
    private SharedPreferences.Editor sped;
    private boolean _termsAccepted, showNav, isLoggedIn, password;
    private Dao<Segment, String> daoSegment;
    private Dao<Form, String> daoForm;
    private Dao<FormScreen, String> daoFormScreen;
    private Dao<FormItem, String> daoFormItem;
    private Dao<FormOption, String> daoFormOption;
    private Dao<FormValue, String> daoFormValue;
    private Dao<CheckItem, String> daoCheckItem;
    private Dao<Category, String> daoCategory;
    private Dao<Registry, String> daoRegistry;
    private Dao<Favourite, String> daoFavourite;
    private Dao<Difficulty, String> daoDifficulty;
    private Dao<Language, String> daoLanguage;
    private Dao<FeedItem, String> daoFeedItem;
    private Dao<FeedSource, String> daoFeedSource;
    private Dao<CustomFeed, String> daoRSS;
    private OrmHelper dbHelper;
    public static Global INSTANCE;
    private boolean needsRefreshActivity;

    @SuppressLint("CommitPrefEdits")
    @Override
    public void onCreate() {
        super.onCreate();
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        Context mContext = getApplicationContext();
        prefs = mContext.getSharedPreferences(
                "org.secfirst.umbrella", Application.MODE_PRIVATE);
        sped = prefs.edit();
        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());
        INSTANCE = this;
//        if (BuildConfig.DEBUG) setupCrashlytics();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public void setLoggedIn(boolean isLoggedIn) {
        this.isLoggedIn = isLoggedIn;
    }

    public void set_termsAccepted(boolean terms) {
        _termsAccepted = terms;
        sped.putBoolean("termsAccepted", _termsAccepted).commit();
    }

    public boolean getTermsAccepted() {
        _termsAccepted = prefs.getBoolean("termsAccepted", false);
        return _termsAccepted;
    }

    public boolean needsRefreshActivity() {
        return needsRefreshActivity;
    }

    public void setNeedsRefreshActivity(boolean needsRefreshActivity) {
        this.needsRefreshActivity = needsRefreshActivity;
    }

    public boolean hasShownNavAlready() {
        showNav = prefs.getBoolean("showNav", false);
        return showNav;
    }

    public void navShown() {
        this.showNav = true;
        sped.putBoolean("showNav", showNav).commit();
    }

    public List<FeedItem> getFeedItems() {
        List<FeedItem> items = new ArrayList<>();
        try {
            items = getDaoFeedItem().queryForAll();
        } catch (SQLiteException | SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    public List<CustomFeed> getCustomFeed() {
        List<CustomFeed> items = new ArrayList<>();
        try {
            items = getDaoRSS().queryForAll();
        } catch (SQLiteException | SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    public boolean getNotificationsEnabled() {
        Registry r = getRegistry("notificationsEnabled");
        boolean enabled = false;
        try {
            if (r != null) enabled = Boolean.valueOf(r.getValue());
        } catch (NumberFormatException nfe) {
            Timber.e(nfe);
        }
        return enabled;
    }

    public void deleteRegistriesByName(String name) {
        try {
            DeleteBuilder<Registry, String> toDelete = getDaoRegistry().deleteBuilder();
            toDelete.where().eq(Registry.FIELD_NAME, name);
            toDelete.delete();
        } catch (SQLiteException | SQLException e) {
            Timber.e(e);
        }
    }

    public boolean hasShownCoachMark(String name) {
        Registry reg = getRegistry(name);
        return reg != null && Boolean.parseBoolean(reg.getValue());
    }

    public void setCoachMarkShown(String name, boolean shown) {
        setRegistry(name, String.valueOf(shown));
    }

    public Registry getRegistry(String name) {
        Registry registry = null;
        try {
            PreparedQuery<Registry> queryBuilder =
                    getDaoRegistry().queryBuilder().where().eq(Registry.FIELD_NAME, name).prepare();
            registry = getDaoRegistry().queryForFirst(queryBuilder);
        } catch (SQLiteException | SQLException e) {
            Timber.e(e);
        }
        return registry;
    }

    public void setRegistry(String name, Object value) {
        Registry registry = null;
        try {
            PreparedQuery<Registry> queryBuilder =
                    getDaoRegistry().queryBuilder().where().eq(Registry.FIELD_NAME, name).prepare();
            registry = getDaoRegistry().queryForFirst(queryBuilder);
        } catch (SQLiteException | SQLException e) {
            Timber.e(e);
        } finally {
            if (registry != null) {
                try {
                    registry.setValue(String.valueOf(value));
                    getDaoRegistry().update(registry);
                } catch (SQLiteException | SQLException e) {
                    Timber.e(e);
                }
            } else {
                try {
                    getDaoRegistry().create(new Registry(name, String.valueOf(value)));
                } catch (SQLiteException | SQLException e) {
                    Timber.e(e);
                }
            }
        }
    }

    public void setNotificationsEnabled(boolean enabled) {
        setRegistry("notificationsEnabled", enabled);
    }

    public boolean getNotificationRingtoneEnabled() {
        Registry r = getRegistry("notificationRingtoneEnabled");
        boolean enabled = true;
        try {
            if (r != null) enabled = Boolean.valueOf(r.getValue());
        } catch (NumberFormatException nfe) {
            Timber.e(nfe);
        }
        return enabled;
    }

    public void setNotificationRingtoneEnabled(boolean enabled) {
        setRegistry("notificationRingtoneEnabled", enabled);
    }

    public Uri getNotificationRingtone() {
        Registry r = getRegistry("notificationRingtone");
        Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        try {
            if (r != null && !r.getValue().equals("")) ringtone = Uri.parse(r.getValue());
        } catch (IllegalArgumentException e) {
            Timber.e(e);
        }
        return ringtone;
    }

    public void setNotificationRingtone(Uri notificationRingtoneUri) {
        if (notificationRingtoneUri == null) return;
        setRegistry("notificationRingtone", notificationRingtoneUri.toString());
    }

    public boolean getNotificationVibrationEnabled() {
        Registry r = getRegistry("notificationVibration");
        boolean enabled = true;
        try {
            if (r != null) enabled = Boolean.valueOf(r.getValue());
        } catch (NumberFormatException nfe) {
            Timber.e(nfe);
        }
        return enabled;
    }

    public void setNotificationVibrationEnabled(boolean enabled) {
        setRegistry("notificationVibration", enabled);
    }

    public boolean getSkipPassword() {
        return prefs.getBoolean("skipPassword", false);
    }

    public void setSkipPassword(boolean skipPassword) {
        sped.putBoolean("skipPassword", skipPassword).commit();
    }

    public boolean hasPasswordSet(boolean withoutSkip) {
        if (withoutSkip) return password;
        else return password || prefs.getBoolean("skipPassword", false);
    }

    public void setPassword(final Context context, SettingsFragment fragment) {
        setPassword(context, fragment, false);
    }

    public void setPassword(final Context context, final SettingsFragment fragment, final boolean change) {
        final AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(change ? R.string.change_password_title : R.string.set_password_title);
        alert.setMessage(R.string.set_password_body);
        View view = LayoutInflater.from(context).inflate(R.layout.password_alert, null);
        final EditText pwOld = (EditText) view.findViewById(R.id.oldpw);
        pwOld.setVisibility(change ? View.VISIBLE : View.GONE);
        final EditText pwInput = (EditText) view.findViewById(R.id.pwinput);
        final EditText confirmInput = (EditText) view.findViewById(R.id.pwconfirm);
        alert.setView(view);
        alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

            }
        });
        alert.setNeutralButton(R.string.skip, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final AlertDialog.Builder alert2 = new AlertDialog.Builder(context);
                alert2.setTitle(R.string.skip_password_title);
                alert2.setMessage(R.string.skip_password_warning);
                alert2.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (password)
                            getOrmHelper().getWritableDatabase(getOrmHelper().getPassword()).rawExecSQL("PRAGMA rekey = '" + new SelectArg(getString(R.string.default_db_password)) + "';");
                        setPassword(context, fragment, change);
                        Intent i = new Intent(context, ((Activity) context).getClass());
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(i);
                        ((Activity) context).overridePendingTransition(0, 0);
                        ((Activity) context).finish();
                    }
                });
                alert2.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setSkipPassword(true);
                    }
                });
                final AlertDialog dialog2 = alert2.create();
                dialog2.show();
            }
        });
        alert.setCancelable(false);
        alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.cancel();
            }
        });
        final AlertDialog dialog = alert.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pw = pwInput.getText().toString();
                String checkError = UmbrellaUtil.checkPasswordStrength(pw, getApplicationContext());
                SQLiteDatabase.loadLibs(context);
                if (change && !checkSQLCipherPW(pwOld.getText().toString(), context)) {
                    Toast.makeText(context, R.string.old_password_incorrect, Toast.LENGTH_LONG).show();
                } else if (!pw.equals(confirmInput.getText().toString())) {
                    Toast.makeText(context, R.string.passwords_do_not_match, Toast.LENGTH_LONG).show();
                } else if (checkError.equals("")) {
                    getOrmHelper().getWritableDatabase(getOrmHelper().getPassword()).rawExecSQL("PRAGMA rekey = '" + new SelectArg(pw) + "';");
                    password = true;
                    setLoggedIn(true);
                    setSkipPassword(false);
                    dialog.dismiss();
                    if (fragment != null) fragment.onResume();
                    Toast.makeText(context, R.string.you_have_successfully_set_your_password, Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(context, ((Activity) context).getClass());
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                    ((Activity) context).overridePendingTransition(0, 0);
                    ((Activity) context).finish();
                } else {
                    Toast.makeText(context, getString(R.string.choose_stronger_password) + checkError, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    public void logout(Context context, boolean redirect) {
        setLoggedIn(false);
        if (OpenHelperManager.getHelper(context, OrmHelper.class) != null) {
            OpenHelperManager.getHelper(context, OrmHelper.class).close();
            OpenHelperManager.setHelper(null);
            dbHelper = null;
        }
        if (redirect) {
            Intent i = new Intent(context, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }

    public void resetPassword(final Context context) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setTitle(getString(R.string.reset_password_title));
        alertDialogBuilder.setMessage(getString(R.string.reset_password_text));
        alertDialogBuilder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                closeDbAndDAOs();
                deleteDatabase(getApplicationContext().getDatabasePath(OrmHelper.DATABASE_NAME));
                removeSharedPreferences();
                Intent i = new Intent(context, TourActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                Toast.makeText(context, R.string.content_reset_to_default, Toast.LENGTH_SHORT).show();
                ((Activity) context).finish();

                password = isLoggedIn = false;
                startActivity(i);
            }
        });
        alertDialogBuilder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public OrmHelper getOrmHelper() {
        SQLiteDatabase.loadLibs(this);
        if (dbHelper == null || !dbHelper.isOpen()) {
            createDatabaseIfNotExists();
            dbHelper = new OrmHelper(getApplicationContext());
        }
        return dbHelper;
    }

    public boolean checkSQLCipherPW(String password, Context context) {
        SQLiteDatabase.loadLibs(this);
        try {
            OrmHelper oh = context != null ? new OrmHelper(context) : getOrmHelper();
            oh.getWritableDatabase(password);
            return true;
        } catch (SQLiteException e) {
            Timber.e(e);
        }
        return false;
    }

    public boolean initializeSQLCipher(String password) {
        if (password.equals("")) password = getString(R.string.default_db_password);
        if (checkSQLCipherPW(password, null)) {
            getDaoSegment();
            getDaoCheckItem();
            getDaoCategory();
            getDaoRegistry();
            getDaoFavourite();
            getDaoDifficulty();
            getDaoLanguage();
            getDaoFeedItem();
            getDaoFeedSource();
            getDaoForm();
            getDaoFormItem();
            getDaoFormOption();
            getDaoFormScreen();
            getDaoFormValue();
            getDaoRSS();
            startService();
            SQLiteDatabase db = getOrmHelper().getWritableDatabase(getOrmHelper().getPassword());
            if (db != null) {
                if (db.getVersion() == 1) db.setVersion(5); // Fix for an old regression
                getOrmHelper().onUpgrade(db, getOrmHelper().getConnectionSource(), db.getVersion(), OrmHelper.DATABASE_VERSION);
            }
            if (!password.equals(getString(R.string.default_db_password))) setLoggedIn(true);
            return true;
        }
        this.password = true;
        return false;
    }

    public void startService() {
        Intent i = new Intent(getApplicationContext(), RefreshService.class);
        i.putExtra("refresh_feed", getRefreshValue());
        startService(i);
    }

    public Dao<Segment, String> getDaoSegment() {
        if (daoSegment == null) {
            try {
                daoSegment = getOrmHelper().getDao(Segment.class);
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoSegment;
    }

    public Dao<CheckItem, String> getDaoCheckItem() {
        if (daoCheckItem == null) {
            try {
                daoCheckItem = getOrmHelper().getDao(CheckItem.class);
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoCheckItem;
    }

    public Dao<Category, String> getDaoCategory() {
        if (daoCategory == null) {
            try {
                daoCategory = getOrmHelper().getDao(Category.class);
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoCategory;
    }

    public Dao<Language, String> getDaoLanguage() {
        if (daoLanguage == null) {
            try {
                daoLanguage = getOrmHelper().getDao(Language.class);
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoLanguage;
    }

    public Dao<FeedItem, String> getDaoFeedItem() {
        if (daoFeedItem == null) {
            try {
                TableUtils.createTableIfNotExists(getOrmHelper().getConnectionSource(), FeedItem.class);
                daoFeedItem = getOrmHelper().getDao(FeedItem.class);
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoFeedItem;
    }

    public Dao<CustomFeed, String> getDaoRSS() {
        if (daoRSS == null) {
            try {
                TableUtils.createTableIfNotExists(getOrmHelper().getConnectionSource(), CustomFeed.class);
                daoRSS = getOrmHelper().getDao(CustomFeed.class);
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoRSS;
    }


    public Dao<Form, String> getDaoForm() {
        if (daoForm == null) {
            try {
                TableUtils.createTableIfNotExists(getOrmHelper().getConnectionSource(), Form.class);
                daoForm = getOrmHelper().getDao(Form.class);
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoForm;
    }

    public Dao<FormScreen, String> getDaoFormScreen() {
        if (daoFormScreen == null) {
            try {
                TableUtils.createTableIfNotExists(getOrmHelper().getConnectionSource(), FormScreen.class);
                daoFormScreen = getOrmHelper().getDao(FormScreen.class);
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoFormScreen;
    }

    public Dao<FormItem, String> getDaoFormItem() {
        if (daoFormItem == null) {
            try {
                TableUtils.createTableIfNotExists(getOrmHelper().getConnectionSource(), FormItem.class);
                daoFormItem = getOrmHelper().getDao(FormItem.class);
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoFormItem;
    }

    public Dao<FormOption, String> getDaoFormOption() {
        if (daoFormOption == null) {
            try {
                TableUtils.createTableIfNotExists(getOrmHelper().getConnectionSource(), FormOption.class);
                daoFormOption = getOrmHelper().getDao(FormOption.class);
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoFormOption;
    }

    public Dao<FormValue, String> getDaoFormValue() {
        if (daoFormValue == null) {
            try {
                TableUtils.createTableIfNotExists(getOrmHelper().getConnectionSource(), FormValue.class);
                daoFormValue = getOrmHelper().getDao(FormValue.class);
                long count = getDaoForm().countOf();
                if (count < 1) {
                    getForms(true);
                } else if (count < 2) {
                    getForms(false);
                }
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoFormValue;
    }

    public Dao<FeedSource, String> getDaoFeedSource() {
        if (daoFeedSource == null) {
            try {
                TableUtils.createTableIfNotExists(getOrmHelper().getConnectionSource(), FeedSource.class);
                daoFeedSource = getOrmHelper().getDao(FeedSource.class);
                if (daoFeedSource.countOf() < 1) {
                    daoFeedSource.create(new FeedSource("ReliefWeb", 0));
                    daoFeedSource.create(new FeedSource("UN", 1));
                    daoFeedSource.create(new FeedSource("FCO", 2));
                    daoFeedSource.create(new FeedSource("CDC", 3));
                    daoFeedSource.create(new FeedSource("Global Disaster and Alert Coordination System", 4));
                    daoFeedSource.create(new FeedSource("US State Department Country Warnings", 5));
                }
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoFeedSource;
    }

    public Dao<Registry, String> getDaoRegistry() {
        if (daoRegistry == null) {
            try {
                daoRegistry = getOrmHelper().getDao(Registry.class);
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoRegistry;
    }

    public Dao<Favourite, String> getDaoFavourite() {
        if (daoFavourite == null) {
            try {
                daoFavourite = getOrmHelper().getDao(Favourite.class);
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoFavourite;
    }

    public Dao<Difficulty, String> getDaoDifficulty() {
        if (daoDifficulty == null) {
            try {
                daoDifficulty = getOrmHelper().getDao(Difficulty.class);
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
        return daoDifficulty;
    }

    public void syncNewCategories(Tree receivedTree, Resources resources) {
        if (getOrmHelper() != null) {
            try {
                TableUtils.dropTable(getOrmHelper().getConnectionSource(), Category.class, true);
                TableUtils.createTable(getOrmHelper().getConnectionSource(), Category.class);
                TableUtils.clearTable(getOrmHelper().getConnectionSource(), Segment.class);
                TableUtils.clearTable(getOrmHelper().getConnectionSource(), Difficulty.class);
                TableUtils.dropTable(getOrmHelper().getConnectionSource(), CheckItem.class, true);
                TableUtils.createTableIfNotExists(getOrmHelper().getConnectionSource(), CheckItem.class);

                Category mySecurity = new Category();
                mySecurity.setCategory(resources.getString(R.string.my_security));
                getDaoCategory().create(mySecurity);
                for (NewCategory item : receivedTree.getCategories()) {
                    Category category = item.getCategory();
                    getDaoCategory().create(category);
                    for (NewCategory newCategory : item.getSubcategories()) {
                        Category subCategory = newCategory.getCategory();
                        subCategory.setHasDifficulty(item.getId().equals("about") | item.getId().equals("tools") ? 0 : 1);
                        if (newCategory.getId().equals("_")) {
                            subCategory.setId(category.getId());
                            subCategory.setCategory(category.getCategory());
                            getDaoCategory().update(subCategory);
                        } else {
                            subCategory.setParent(category.getId());
                            getDaoCategory().create(subCategory);
                        }
                        ArrayList<NewDifficulty> nd = newCategory.getDifficulties();
                        for (NewDifficulty difficulty : nd) {

                            for (Segment segment : difficulty.getSegments()) {
                                segment.setCategory(subCategory.getId());
                                segment.setDifficulty(UmbrellaUtil.getDifficultyFromString(difficulty.getId()));
                                segment.setDifficultyString(difficulty.getId());
                                getDaoSegment().create(segment);
                            }

                            for (CheckItem checkItem : difficulty.getCheckItems()) {
                                checkItem.setCategory(subCategory.getId());
                                checkItem.setDifficulty(UmbrellaUtil.getDifficultyFromString(difficulty.getId()));
                                checkItem.setDifficultyString(difficulty.getId());
                                checkItem.setText("");
                                getDaoCheckItem().create(checkItem);
                            }

                        }
                    }
                }

                if (receivedTree.getForms() != null) {
                    if (getOrmHelper() != null) {
                        try {
                            TableUtils.dropTable(getOrmHelper().getConnectionSource(), Form.class, true);
                            TableUtils.dropTable(getOrmHelper().getConnectionSource(), FormScreen.class, true);
                            TableUtils.dropTable(getOrmHelper().getConnectionSource(), FormItem.class, true);
                            TableUtils.dropTable(getOrmHelper().getConnectionSource(), FormOption.class, true);
                            TableUtils.clearTable(getOrmHelper().getConnectionSource(), FormValue.class);
                            TableUtils.createTableIfNotExists(getOrmHelper().getConnectionSource(), Form.class);
                            TableUtils.createTableIfNotExists(getOrmHelper().getConnectionSource(), FormScreen.class);
                            TableUtils.createTableIfNotExists(getOrmHelper().getConnectionSource(), FormItem.class);
                            TableUtils.createTableIfNotExists(getOrmHelper().getConnectionSource(), FormOption.class);
                        } catch (SQLiteException | SQLException e) {
                            Timber.e(e);
                        }
                    }
                    for (Form form : receivedTree.getForms()) {
                        try {
                            getDaoForm().create(form);
                            ForeignCollection<FormScreen> screens = getDaoForm().getEmptyForeignCollection("screens");
                            for (FormScreen formScreen : form.getScreenArrayList()) {
                                formScreen.setForm(form);
                                screens.add(formScreen);
                                ForeignCollection<FormItem> items = getDaoFormScreen().getEmptyForeignCollection("items");
                                for (FormItem formItem : formScreen.getItemArrayList()) {
                                    formItem.setFormScreen(formScreen);
                                    items.add(formItem);
                                    ForeignCollection<FormOption> options = getDaoFormItem().getEmptyForeignCollection("options");
                                    for (FormOption formOption : formItem.getOptionArrayList()) {
                                        formOption.setFormItem(formItem);
                                        options.add(formOption);
                                    }

                                }


                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }

                }
            } catch (SQLiteException | SQLException e) {
                Timber.e(e);
            }
        }
    }

    public void syncApi(final Context context, final SyncProgressListener listener) {

        UmbrellaRestClient.get("api/tree?content=html", null, null, context, new JsonHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                super.onSuccess(statusCode, headers, response);
                listener.onProgressChange(77);
                Gson gson = new GsonBuilder().
                        registerTypeAdapter(Form.class, new FormDeserializer()).
                        create();
                Type treeType = new TypeToken<Tree>() {
                }.getType();
                final Tree receivedTree = gson.fromJson(response.toString(), treeType);
                if (receivedTree != null) {
                    for (String s : receivedTree.getAssets()) {
                        File file = new File(getFilesDir()+"/"+s);
                        if(file.exists() && file.length()>0) {
                            Timber.d("Already exists as %s, length %d", file.getAbsolutePath(), file.length());
                        } else {
                            PRDownloader.download(UmbrellaRestClient.getAbsoluteUrl(String.format("api/repo/asset/%s", s)), getFilesDir().getAbsolutePath(), s).build().start(new OnDownloadListener() {
                                @Override
                                public void onDownloadComplete() {
                                    Timber.d(s);
                                }

                                @Override
                                public void onError(Error error) {
                                    Timber.e(error.toString());
                                }
                            });
                        }
                    }

                    listener.onProgressChange(88);
                    Registry language = Global.INSTANCE.getRegistry("language");
                    Configuration conf = getResources().getConfiguration();
                    if (language != null && !language.getValue().equals("")) {
                        conf.locale = new Locale(language.getValue());
                    }

                    final Resources resources = new Resources(getAssets(), new DisplayMetrics(), conf);
                    DisplayMetrics dm = resources.getDisplayMetrics();
                    resources.updateConfiguration(conf, dm);

                    listener.onStatusChange(resources.getString(R.string.updating_the_database));
                    new Thread() {
                        @Override
                        public void run() {
                            Global.INSTANCE.syncNewCategories(receivedTree, resources);
                            ((Activity) context).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    listener.onProgressChange(100);
                                    listener.onStatusChange(getString(R.string.sync_complete));
                                    listener.onDone();
                                    setNeedsRefreshActivity(true);
                                }
                            });
                        }

                        ;
                    }.start();
                } else {
                    listener.onDone();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                super.onFailure(statusCode, headers, responseString, throwable);
                listener.onDone();
                setNeedsRefreshActivity(false);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                listener.onDone();
                setNeedsRefreshActivity(false);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                listener.onDone();
                setNeedsRefreshActivity(false);
            }

            @Override
            public void onProgress(long bytesWritten, long totalSize) {
                super.onProgress(bytesWritten, totalSize);
                if (bytesWritten < totalSize) {
                    listener.onProgressChange((int) ((bytesWritten / totalSize) * 0.66));
                } else {
                    listener.onProgressChange(44);
                }
            }

            @Override
            public void onStart() {
                super.onStart();
                listener.onProgressChange(22);
            }

            @Override
            public void onFinish() {
                super.onFinish();
                listener.onProgressChange(66);
            }
        });
    }

    public String getRefreshLabel(Integer refreshValue) {
        String refreshValueLabel = "";
        if (refreshValue == null) refreshValue = getRefreshValue();
        HashMap<String, Integer> refreshValues = UmbrellaUtil.getRefreshValues(getApplicationContext());
        for (Map.Entry<String, Integer> entry : refreshValues.entrySet()) {
            if (entry.getValue().equals(refreshValue)) {
                refreshValueLabel = entry.getKey();
            }
        }
        return refreshValueLabel;
    }

    public ArrayList<Integer> getSelectedFeedSources() {
        final CharSequence[] items = {"ReliefWeb", "UN", "FCO", "CDC", "Global Disaster and Alert Coordination System", "US State Department Country Warnings"};
        final ArrayList<Integer> selectedItems = new ArrayList<>();
        List<Registry> selections;
        try {
            selections = getDaoRegistry().queryForEq(Registry.FIELD_NAME, "feed_sources");
            for (int i = 0; i < items.length; i++) {
                for (Registry reg : selections) {
                    if (reg.getValue().equals(String.valueOf(i))) {
                        selectedItems.add(i);
                        break;
                    }
                }
            }
        } catch (SQLiteException | SQLException e) {
            Timber.e(e);
        }
        return selectedItems;
    }

    public String getSelectedFeedSourcesLabel(boolean inline) {
        String feedSourcesLabel = "";
        final CharSequence[] items = {"ReliefWeb", "UN", "FCO", "CDC", "Global Disaster and Alert Coordination System", "US State Department Country Warnings"};
        final ArrayList<Integer> selectedItems = getSelectedFeedSources();
        for (Integer selectedItem : selectedItems) {
            if (!selectedItem.equals(selectedItems.get(0))) {
                feedSourcesLabel += (inline) ? ", " : "\n";
            }
            feedSourcesLabel += (inline ? "" : " - ") + items[selectedItem];
        }
        return feedSourcesLabel;
    }

    public String getChosenCountry() {
        String selectedCountry = "";
        Dao<Registry, String> regDao = getDaoRegistry();
        List<Registry> selCountry = null;
        try {
            selCountry = regDao.queryForEq(Registry.FIELD_NAME, "country");
        } catch (SQLiteException | SQLException e) {
            Timber.e(e);
        }
        if (selCountry != null && !selCountry.isEmpty()) {
            selectedCountry = selCountry.get(0).getValue();
        }
        return selectedCountry;
    }

    public int getRefreshValue() {
        int retInterval = 0;
        try {
            List<Registry> selInterval = getDaoRegistry().queryForEq(Registry.FIELD_NAME, "refresh_value");
            if (!selInterval.isEmpty()) {
                try {
                    retInterval = Integer.parseInt(selInterval.get(0).getValue());
                } catch (NumberFormatException nfe) {
                    Timber.e(nfe);
                }
            }
        } catch (SQLiteException | SQLException e) {
            Timber.e(e);
        }
        return retInterval;
    }

    public void setRefreshValue(int refreshValue) {
        try {
            List<Registry> selInterval = getDaoRegistry().queryForEq(Registry.FIELD_NAME, "refresh_value");
            if (!selInterval.isEmpty()) {
                selInterval.get(0).setValue(String.valueOf(refreshValue));
                getDaoRegistry().update(selInterval.get(0));
            } else {
                getDaoRegistry().create(new Registry("refresh_value", String.valueOf(refreshValue)));
            }
        } catch (SQLiteException | SQLException e) {
            Timber.e(e);
        }
    }

    public long getFeedItemsRefreshed() {
        long feedItemsRefreshed = 0L;
        QueryBuilder<FeedItem, String> qb = getDaoFeedItem().queryBuilder();
        try {
            qb.orderBy(FeedItem.FIELD_UPDATED_AT, false);
            FeedItem firstFeedItem = qb.queryForFirst();
            if (firstFeedItem != null) {
                feedItemsRefreshed = firstFeedItem.getDate() * 1000;
            }
        } catch (SQLiteException | SQLException e) {
            e.printStackTrace();
        }
        return feedItemsRefreshed;
    }

    public void createDatabaseIfNotExists() {
        File destFile = getApplicationContext().getDatabasePath(OrmHelper.DATABASE_NAME);
        if (!destFile.exists()) {
            try {
                copyDataBase(destFile);
            } catch (IOException e) {
                Timber.e(e);
            }
        }
    }

    private void copyDataBase(File destFile) throws IOException {
        destFile.getParentFile().mkdirs();
        InputStream externalDbStream = getAssets().open(OrmHelper.DATABASE_NAME);
        String outFileName = destFile.getPath();
        OutputStream localDbStream = new FileOutputStream(outFileName);
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = externalDbStream.read(buffer)) > 0) {
            localDbStream.write(buffer, 0, bytesRead);
        }
        localDbStream.close();
        externalDbStream.close();

    }

    public static boolean deleteDatabase(File file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }

        boolean deleted = file.delete();
        deleted |= new File(file.getPath() + "-journal").delete();
        deleted |= new File(file.getPath() + "-shm").delete();
        deleted |= new File(file.getPath() + "-wal").delete();

        File dir = file.getParentFile();
        if (dir != null) {
            final String prefix = file.getName() + "-mj";
            final FileFilter filter = new FileFilter() {
                @Override
                public boolean accept(File candidate) {
                    return candidate.getName().startsWith(prefix);
                }
            };
            for (File masterJournal : dir.listFiles(filter)) {
                deleted |= masterJournal.delete();
            }
        }
        return deleted;
    }

    public void removeSharedPreferences() {
        File sharedPreferenceFile = new File(getFilesDir().getPath().replaceFirst("/files$", "/shared_prefs/"));
        File[] listFiles = sharedPreferenceFile.listFiles();
        for (File file : listFiles) {
            file.delete();
        }
    }

    public ArrayList<FeedSource> getFeedSourcesList() {
        ArrayList<FeedSource> sourcesList = new ArrayList<>();
        sourcesList.add(new FeedSource("UN / ReliefWeb", 0));
        sourcesList.add(new FeedSource("CDC", 3));
        sourcesList.add(new FeedSource("Global Disaster and Alert Coordination System", 4));
        sourcesList.add(new FeedSource("US State Department Country Warnings", 5));
        return sourcesList;
    }

    public CharSequence[] getFeedSourcesArray() {
        List<FeedSource> feedSources = getFeedSourcesList();
        List<String> sourcesList = new ArrayList<>();
        for (FeedSource source : feedSources) {
            sourcesList.add(source.getName());
        }
        return sourcesList.toArray(new CharSequence[sourcesList.size()]);
    }

    public int getFeedSourceCodeByIndex(int index) {
        List<FeedSource> feedSources = getFeedSourcesList();
        if (index < feedSources.size()) {
            return feedSources.get(index).getCode();
        }
        return -1;
    }

    public void closeDbAndDAOs() {
        getOrmHelper().close();
        daoSegment = null;
        daoCheckItem = null;
        daoCategory = null;
        daoRegistry = null;
        daoFavourite = null;
        daoDifficulty = null;
        daoLanguage = null;
        daoFeedItem = null;
        daoFeedSource = null;
        daoForm = null;
        daoFormScreen = null;
        daoFormItem = null;
        daoFormOption = null;
        daoFormValue = null;
    }

    public ArrayList<Form> getForms(boolean alsoFirst) {
        ArrayList<Form> forms = new ArrayList<>();
        return forms;
    }

    private void setupCrashlytics() {
        // Set up Crashlytics, disabled for debug builds
        Fabric.with(this, new Crashlytics());
    }
}