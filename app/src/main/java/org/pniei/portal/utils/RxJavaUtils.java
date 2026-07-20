package org.pniei.portal.utils;

import android.widget.SearchView;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import org.pniei.portal.database.SpoContact;
import org.pniei.portal.database.DBUtils;

public class RxJavaUtils {
    public static Observable<List<SpoContact>> getObserverSearchContact(SearchView searchView) {
        return Observable.create((ObservableOnSubscribe<String>) emitter -> searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        emitter.onNext(query);
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        emitter.onNext(newText);
                        return false;
                    }
                }))
                .debounce(800, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .switchMap(RxJavaUtils::dataFromDB)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());


    }

    private static Observable<List<SpoContact>> dataFromDB(final String str) {
        return Observable.just(str).map(s -> Arrays.asList(DBUtils.getSearchContact(s)));
    }

}
