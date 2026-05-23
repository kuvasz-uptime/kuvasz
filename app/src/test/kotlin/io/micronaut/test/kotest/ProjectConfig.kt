package io.micronaut.test.kotest

import com.kuvaszuptime.kuvasz.testutils.TestDbContainer
import com.kuvaszuptime.kuvasz.testutils.TestSMTPExtension
import io.kotest.core.config.AbstractProjectConfig
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers

object ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(TestSMTPExtension, MicronautKotest5Extension)

    override suspend fun beforeProject() {
        TestDbContainer.start()
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
    }

    override suspend fun afterProject() {
        TestDbContainer.stop()
        RxJavaPlugins.reset()
    }
}
