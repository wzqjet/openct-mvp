package cc.metapro.openct.grades;

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
import android.support.annotation.Keep;
import android.widget.TextView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cc.metapro.openct.R;
import cc.metapro.openct.data.openctservice.ServiceGenerator;
import cc.metapro.openct.data.source.DBManger;
import cc.metapro.openct.data.source.Loader;
import cc.metapro.openct.data.university.item.GradeInfo;
import cc.metapro.openct.grades.cet.CETService;
import cc.metapro.openct.utils.ActivityUtils;
import cc.metapro.openct.utils.Constants;
import cc.metapro.openct.utils.HTMLUtils.Form;
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
class GradePresenter implements GradeContract.Presenter {

    private Context mContext;
    private GradeContract.View mGradeFragment;
    private List<GradeInfo> mGrades;

    GradePresenter(GradeContract.View view, Context context) {
        mContext = context;
        mGradeFragment = view;
        mGradeFragment.setPresenter(this);
    }

    @Override
    public Disposable loadTargetPage(final String code) {
        ActivityUtils.getProgressDialog(mContext, R.string.loading_grade_page).show();
        return Observable.create(new ObservableOnSubscribe<Form>() {
            @Override
            public void subscribe(ObservableEmitter<Form> e) throws Exception {
                Map<String, String> loginMap = Loader.getCmsStuInfo(mContext);
                loginMap.put(mContext.getString(R.string.key_captcha), code);
                Form form = Loader.getCms(mContext).getGradePageForm(loginMap);
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
                    public void accept(Form form) throws Exception {
                        ActivityUtils.dismissProgressDialog();
                        if (form != null) {
                            mGradeFragment.showFormDialog(form);
                        } else {
                            Toast.makeText(mContext, R.string.load_grade_page_fail, Toast.LENGTH_LONG).show();
                        }
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
                .doOnComplete(new Action() {
                    @Override
                    public void run() throws Exception {
                        ActivityUtils.dismissProgressDialog();
                        Toast.makeText(mContext, R.string.load_grade_page_fail, Toast.LENGTH_SHORT).show();
                    }
                })
                .subscribe();
    }

    @Override
    public Disposable loadQuery(final String actionURL, final Map<String, String> queryMap) {
        ActivityUtils.getProgressDialog(mContext, R.string.loading_grades).show();
        return Observable
                .create(new ObservableOnSubscribe<List<GradeInfo>>() {
                    @Override
                    public void subscribe(ObservableEmitter<List<GradeInfo>> e) throws Exception {
                        e.onNext(Loader.getCms(mContext).getGrades(actionURL, queryMap));
                        e.onComplete();
                    }
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Consumer<List<GradeInfo>>() {
                    @Override
                    public void accept(List<GradeInfo> infos) throws Exception {
                        ActivityUtils.dismissProgressDialog();
                        if (infos.size() == 0) {
                            Toast.makeText(mContext, R.string.no_grades_avail, Toast.LENGTH_SHORT).show();
                        } else {
                            mGrades = infos;
                            storeGrades();
                            loadLocalGrades();
                        }
                    }
                })
                .onErrorReturn(new Function<Throwable, List<GradeInfo>>() {
                    @Override
                    public List<GradeInfo> apply(Throwable throwable) throws Exception {
                        ActivityUtils.dismissProgressDialog();
                        Toast.makeText(mContext, throwable.getMessage(), Toast.LENGTH_SHORT).show();
                        return new ArrayList<>(0);
                    }
                }).subscribe();
    }

    @Override
    public void loadLocalGrades() {
        Observable
                .create(new ObservableOnSubscribe<List<GradeInfo>>() {
                    @Override
                    public void subscribe(ObservableEmitter<List<GradeInfo>> e) throws Exception {
                        DBManger manger = DBManger.getInstance(mContext);
                        e.onNext(manger.getGradeInfos());
                        e.onComplete();
                    }
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Consumer<List<GradeInfo>>() {
                    @Override
                    public void accept(List<GradeInfo> gradeInfos) throws Exception {
                        if (gradeInfos.size() == 0) {
                            Toast.makeText(mContext, R.string.no_local_grades_avail, Toast.LENGTH_SHORT).show();
                        } else {
                            mGrades = gradeInfos;
                            mGradeFragment.onLoadGrades(mGrades);
                        }
                    }
                })
                .onErrorReturn(new Function<Throwable, List<GradeInfo>>() {
                    @Override
                    public List<GradeInfo> apply(Throwable throwable) throws Exception {
                        Toast.makeText(mContext, mContext.getString(R.string.something_wrong) + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                        return new ArrayList<>(0);
                    }
                }).subscribe();
    }

    @Override
    public void loadCETGrade(final Map<String, String> queryMap) {
        Observable
                .create(new ObservableOnSubscribe<Map<String, String>>() {
                    @Override
                    public void subscribe(ObservableEmitter<Map<String, String>> e) throws Exception {
                        CETService service = ServiceGenerator.createCETService();
                        String queryResult = service.queryCET(
                                mContext.getString(R.string.chsi_referer),
                                queryMap.get(mContext.getString(R.string.key_ticket_num)),
                                queryMap.get(mContext.getString(R.string.key_full_name)), "t")
                                .execute().body();

                        Document document = Jsoup.parse(queryResult);
                        Elements elements = document.select("table[class=cetTable]");
                        Element targetTable = elements.first();
                        Elements tds = targetTable.getElementsByTag("td");
                        String name = tds.get(0).text();
                        String school = tds.get(1).text();
                        String type = tds.get(2).text();
                        String num = tds.get(3).text();
                        String time = tds.get(4).text();
                        String grade = tds.get(5).text();

                        Map<String, String> results = new HashMap<>(6);
                        results.put(mContext.getString(R.string.key_full_name), name);
                        results.put(mContext.getString(R.string.key_school), school);
                        results.put(mContext.getString(R.string.key_cet_type), type);
                        results.put(mContext.getString(R.string.key_ticket_num), num);
                        results.put(mContext.getString(R.string.key_cet_time), time);
                        results.put(mContext.getString(R.string.key_cet_grade), grade);

                        e.onNext(results);
                    }
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Consumer<Map<String, String>>() {
                    @Override
                    public void accept(Map<String, String> stringMap) throws Exception {
                        mGradeFragment.onLoadCETGrade(stringMap);
                    }
                })
                .onErrorReturn(new Function<Throwable, Map<String, String>>() {
                    @Override
                    public Map<String, String> apply(Throwable throwable) throws Exception {
                        Toast.makeText(mContext, R.string.load_cet_grade_fail, Toast.LENGTH_SHORT).show();
                        return new HashMap<>();
                    }
                })
                .subscribe();
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
    public void storeGrades() {
        try {
            DBManger manger = DBManger.getInstance(mContext);
            manger.updateGradeInfos(mGrades);
        } catch (Exception e) {
            Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void clearGrades() {
        mGrades = new ArrayList<>(0);
        storeGrades();
    }

    @Override
    public void start() {
        loadLocalGrades();
    }
}
