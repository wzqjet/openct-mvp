package cc.metapro.openct.utils;

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

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.concurrent.TimeUnit;

import cc.metapro.openct.data.openctservice.QuotePreservingCookieJar;
import cc.metapro.openct.data.university.UniversityService;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class SchoolInterceptor implements Interceptor {

    private redirectObserver<String> mObserver;
    private String mURL;
    private boolean redirected;

    public SchoolInterceptor(String URL) {
        mURL = URL.substring(0, URL.lastIndexOf("/"));
    }

    public void setObserver(redirectObserver<String> observer) {
        mObserver = observer;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        if (response.code() == 302 && !redirected) {
            redirected = true;
            String location = response.headers().get("Location");
            if (location.startsWith("/")) {
                location = mURL + location;
            }
            if (mObserver != null) {
                mObserver.onRedirect(location);
            }
        }
        return response;
    }

    public UniversityService createSchoolService() {
        return new Retrofit.Builder()
                .baseUrl("http://openct.metapro.cc/")
                .client(new OkHttpClient.Builder()
                        .addNetworkInterceptor(this)
                        .followRedirects(true)
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .cookieJar(new QuotePreservingCookieJar(new CookieManager() {{
                            setCookiePolicy(CookiePolicy.ACCEPT_ALL);
                        }})).build()
                )
                .addConverterFactory(ScalarsConverterFactory.create())
                .build().create(UniversityService.class);
    }

    public interface redirectObserver<T> {

        void onRedirect(T x);

    }
}
