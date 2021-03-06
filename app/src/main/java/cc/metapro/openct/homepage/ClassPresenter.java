package cc.metapro.openct.homepage;

/*
 *  Copyright 2016 - 2017 OpenCT open source class table
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Version;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cc.metapro.openct.R;
import cc.metapro.openct.data.source.DBManger;
import cc.metapro.openct.data.source.Loader;
import cc.metapro.openct.data.university.item.ClassInfo;
import cc.metapro.openct.data.university.item.EnrichedClassInfo;
import cc.metapro.openct.utils.ActivityUtils;
import cc.metapro.openct.utils.Constants;
import cc.metapro.openct.utils.HTMLUtils.Form;
import cc.metapro.openct.widget.DailyClassWidget;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

@Keep
class ClassPresenter implements ClassContract.Presenter {

    private static final String TAG = ClassPresenter.class.getSimpleName();
    private static boolean showedNotice;
    private ClassContract.View mView;
    private List<EnrichedClassInfo> mEnrichedClasses;
    private Context mContext;

    ClassPresenter(@NonNull ClassContract.View view, Context context) {
        mView = view;
        mView.setPresenter(this);
        mContext = context;
    }

    @Override
    public Disposable loadTargetPage(final String code) {
        ActivityUtils.getProgressDialog(mContext, R.string.loading_class_page).show();
        return Observable.create(new ObservableOnSubscribe<Form>() {
            @Override
            public void subscribe(ObservableEmitter<Form> e) throws Exception {
                Map<String, String> loginMap = Loader.getCmsStuInfo(mContext);
                loginMap.put(mContext.getString(R.string.key_captcha), code);
                Form form = Loader.getCms(mContext).getClassPageFrom(loginMap);
                if (form != null) {
                    e.onNext(form);
                } else {
                    e.onComplete();
                }
            }
        })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Consumer<Form>() {
                    @Override
                    public void accept(Form classes) throws Exception {
                        ActivityUtils.dismissProgressDialog();
                        mView.showFormDialog(classes);
                    }
                })
                .onErrorReturn(new Function<Throwable, Form>() {
                    @Override
                    public Form apply(Throwable throwable) throws Exception {
                        ActivityUtils.dismissProgressDialog();
                        Toast.makeText(mContext, throwable.getMessage(), Toast.LENGTH_SHORT).show();
                        return new Form();
                    }
                })
                .subscribe();
    }

    @Override
    public Disposable loadQuery(final String actionURL, final Map<String, String> queryMap) {
        ActivityUtils.getProgressDialog(mContext, R.string.loading_classes).show();
        return Observable
                .create(new ObservableOnSubscribe<List<EnrichedClassInfo>>() {
                    @Override
                    public void subscribe(ObservableEmitter<List<EnrichedClassInfo>> e) throws Exception {
                        e.onNext(Loader.getCms(mContext).getClasses(actionURL, queryMap));
                        e.onComplete();
                    }
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Consumer<List<EnrichedClassInfo>>() {
                    @Override
                    public void accept(List<EnrichedClassInfo> infos) throws Exception {
                        ActivityUtils.dismissProgressDialog();
                        if (infos.size() == 0) {
                            Toast.makeText(mContext, R.string.no_grades_avail, Toast.LENGTH_SHORT).show();
                        } else {
                            mEnrichedClasses = infos;
                            storeClasses();
                            loadLocalClasses();
                        }
                    }
                })
                .onErrorReturn(new Function<Throwable, List<EnrichedClassInfo>>() {
                    @Override
                    public List<EnrichedClassInfo> apply(Throwable throwable) throws Exception {
                        ActivityUtils.dismissProgressDialog();
                        Toast.makeText(mContext, throwable.getMessage(), Toast.LENGTH_SHORT).show();
                        return new ArrayList<>(0);
                    }
                }).subscribe();
    }

    @Override
    public void loadLocalClasses() {
        try {
            DBManger manger = DBManger.getInstance(mContext);
            mEnrichedClasses = manger.getClassInfos();
            if (mEnrichedClasses.size() == 0) {
                if (!showedNotice) {
                    showedNotice = true;
                    Toast.makeText(mContext, R.string.no_local_classes_avail, Toast.LENGTH_LONG).show();
                }
            } else {
                mView.updateClasses(mEnrichedClasses);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public Disposable loadCaptcha(final TextView view) {
        return Observable
                .create(new ObservableOnSubscribe<String>() {
                    @Override
                    public void subscribe(ObservableEmitter e) throws Exception {
                        Loader.getCms(mContext).getCAPTCHA();
                        e.onComplete();
                    }
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .onErrorReturn(new Function<Throwable, String>() {
                    @Override
                    public String apply(Throwable throwable) throws Exception {
                        throwable.printStackTrace();
                        Toast.makeText(mContext, "获取验证码失败\n" + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                        return "";
                    }
                })
                .doOnComplete(new Action() {
                    @Override
                    public void run() throws Exception {
                        Drawable drawable = BitmapDrawable.createFromPath(Constants.CAPTCHA_FILE);
                        if (drawable != null) {
                            view.setBackground(drawable);
                            view.setText("");
                        }
                    }
                })
                .subscribe();
    }

    @Override
    public void storeClasses() {
        try {
            DBManger manger = DBManger.getInstance(mContext);
            manger.updateClasses(mEnrichedClasses);
            DailyClassWidget.update(mContext);
        } catch (Exception e) {
            Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void exportClasses() {
        ActivityUtils.getProgressDialog(mContext, R.string.creating_class_ical).show();
        Observable
                .create(new ObservableOnSubscribe<Calendar>() {
                    @Override
                    public void subscribe(ObservableEmitter<Calendar> e) throws Exception {
                        FileOutputStream fos = null;
                        try {
                            int week = Loader.getCurrentWeek(mContext);
                            Calendar calendar = new Calendar();
                            calendar.getProperties().add(new ProdId("-//OpenCT Jeff//iCal4j 2.0//EN"));
                            calendar.getProperties().add(Version.VERSION_2_0);
                            calendar.getProperties().add(CalScale.GREGORIAN);
                            for (EnrichedClassInfo c : mEnrichedClasses) {
                                List<ClassInfo> classes = c.getAllClasses();
                                for (ClassInfo classInfo : classes) {
                                    VEvent event = classInfo.getEvent(week, c.getWeekDay());
                                    if (event != null) {
                                        calendar.getComponents().add(event);
                                    }
                                }
                            }
                            calendar.validate();

                            File downloadDir = Environment.getExternalStorageDirectory();
                            if (!downloadDir.exists()) {
                                downloadDir.createNewFile();
                            }

                            File file = new File(downloadDir, "OpenCT_Classes.ics");
                            fos = new FileOutputStream(file);
                            CalendarOutputter calOut = new CalendarOutputter();
                            calOut.output(calendar, fos);
                            e.onComplete();
                        } catch (Exception e1) {
                            Log.e(TAG, e1.getMessage(), e1);
                            e.onError(e1);
                        } finally {
                            if (fos != null) {
                                try {
                                    fos.close();
                                } catch (Exception e1) {
                                    Log.e(TAG, e1.getMessage(), e1);
                                }
                            }
                        }
                    }
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete(new Action() {
                    @Override
                    public void run() throws Exception {
                        ActivityUtils.dismissProgressDialog();
                        Toast.makeText(mContext, R.string.ical_create_success, Toast.LENGTH_LONG).show();
                    }
                })
                .onErrorReturn(new Function<Throwable, Calendar>() {
                    @Override
                    public Calendar apply(Throwable throwable) throws Exception {
                        ActivityUtils.dismissProgressDialog();
                        Log.e(TAG, throwable.getMessage(), throwable);
                        Toast.makeText(mContext, "创建日历信息时发生了异常\n" + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                        return new Calendar();
                    }
                })
                .subscribe();
    }

    @Override
    public void loadFromExcel() {

    }

    @Override
    public void start() {
        loadLocalClasses();
    }
}
