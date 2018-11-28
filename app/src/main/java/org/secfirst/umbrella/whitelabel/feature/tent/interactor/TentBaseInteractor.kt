package org.secfirst.umbrella.whitelabel.feature.tent.interactor

import org.secfirst.umbrella.whitelabel.feature.base.interactor.BaseInteractor
import java.io.File

interface TentBaseInteractor : BaseInteractor {

    suspend fun updateRepository(): Boolean

    suspend fun fetchRepository(): Boolean

    suspend fun loadElementsFile(): List<Pair<String,File>>

    fun loadCategoryImage(imgName: String): String

    fun loadFile(): List<Pair<String,File>>

}