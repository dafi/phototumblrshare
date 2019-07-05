package com.ternaryop.photoshelf.dialogs

import android.content.Context
import com.ternaryop.photoshelf.api.ApiManager
import com.ternaryop.photoshelf.customsearch.GoogleCustomSearchClient
import com.ternaryop.photoshelf.db.DBHelper
import io.reactivex.Maybe
import io.reactivex.Single

/**
 * Created by dave on 24/02/18.
 * Search for misspelled names
 */
class MisspelledName(val context: Context) {
    fun getMisspelledInfo(name: String): Single<Pair<Int, String>> {
        return getMatchingName(name)
            .switchIfEmpty(Maybe.defer { getMisspelledName(name) })
            .switchIfEmpty(Maybe.just(Pair(NAME_NOT_FOUND, name)))
            .toSingle()
    }

    private fun getMatchingName(name: String): Maybe<Pair<Int, String>> {
        return Maybe
            .fromCallable { DBHelper.getInstance(context).tagMatcherDAO.getMatchingTag(name) }
            .switchIfEmpty(Maybe.defer { getCorrectMisspelledName(name) })
            .map { correctedName ->
                if (name.equals(correctedName, ignoreCase = true)) {
                    Pair(NAME_ALREADY_EXISTS, name)
                } else {
                    Pair(NAME_MISSPELLED, correctedName)
                }
            }
    }

    private fun getCorrectMisspelledName(name: String): Maybe<String> {
        return ApiManager.postService().getCorrectMisspelledName(name)
            .flatMapMaybe {
                val corrected = it.response.corrected
                if (corrected == null) {
                    Maybe.empty()
                } else {
                    DBHelper.getInstance(context).tagMatcherDAO.insert(corrected)
                    Maybe.just(corrected)
                }
            }
    }

    private fun getMisspelledName(name: String): Maybe<Pair<Int, String>> {
        return GoogleCustomSearchClient.getCorrectedQuery(name)
            .flatMapMaybe { result ->
                if (result.spelling?.correctedQuery == null) {
                    Maybe.empty()
                } else {
                    Maybe.just(Pair(NAME_MISSPELLED, result.spelling.correctedQuery))
                }
            }
    }

    companion object {
        const val NAME_ALREADY_EXISTS = 0
        const val NAME_NOT_FOUND = 1
        const val NAME_MISSPELLED = 2
    }
}