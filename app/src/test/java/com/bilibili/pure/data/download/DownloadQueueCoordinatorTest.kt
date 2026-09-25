package com.bilibili.pure.data.download

import com.bilibili.pure.data.model.DownloadInfo
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadQueueCoordinatorTest {

    @Test
    fun enqueuePersistsAllFifteenPagesBeforeAnyUrlIsResolved() {
        val ownerScope = newScope()
        val resolverEntered = CompletableDeferred<Unit>()
        val releaseResolver = CompletableDeferred<Unit>()
        val persisted = mutableListOf<DownloadInfo>()
        val transferred = AtomicInteger()

        try {
            val coordinator = DownloadQueueCoordinator(
                scope = ownerScope,
                persistPending = { requests ->
                    requests.also { persisted.addAll(it) }
                },
                resolveUrl = {
                    resolverEntered.complete(Unit)
                    releaseResolver.await()
                    url(it)
                },
                startTransfer = { _, _ -> transferred.incrementAndGet() },
                markFailed = {}
            )

            coordinator.enqueue(pages(15))

            assertEquals((1..15).toList(), persisted.map { it.page })
            assertEquals(0, transferred.get())
            runBlocking {
                withTimeout(2_000) { resolverEntered.await() }
            }
            assertEquals(0, transferred.get())
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun cancellingCallerScopeKeepsOwnedQueueProcessingEveryPage() {
        val ownerScope = newScope()
        val callerScope = newScope()
        val allTransferred = CompletableDeferred<Unit>()
        val transferred = AtomicInteger()

        try {
            val coordinator = DownloadQueueCoordinator(
                scope = ownerScope,
                persistPending = { it },
                resolveUrl = { download ->
                    delay(1)
                    url(download)
                },
                startTransfer = { _, _ ->
                    if (transferred.incrementAndGet() == 15) {
                        allTransferred.complete(Unit)
                    }
                },
                markFailed = {}
            )

            runBlocking {
                callerScope.launch { coordinator.enqueue(pages(15)) }.join()
            }
            callerScope.cancel()

            runBlocking {
                withTimeout(5_000) { allTransferred.await() }
            }
            assertEquals(15, transferred.get())
        } finally {
            callerScope.cancel()
            ownerScope.cancel()
        }
    }

    @Test
    fun oneFailedPageKeepsTheOtherFourteenQueued() {
        val ownerScope = newScope()
        val allFinished = CompletableDeferred<Unit>()
        val transferredPages = mutableListOf<Int>()
        val failedPages = mutableListOf<Int>()
        val finished = AtomicInteger()

        fun trackFinished() {
            if (finished.incrementAndGet() == 15) {
                allFinished.complete(Unit)
            }
        }

        try {
            val coordinator = DownloadQueueCoordinator(
                scope = ownerScope,
                persistPending = { it },
                resolveUrl = { download ->
                    if (download.page == 7) {
                        throw IllegalStateException("page 7 unavailable")
                    }
                    url(download)
                },
                startTransfer = { download, _ ->
                    transferredPages.add(download.page)
                    trackFinished()
                },
                markFailed = { download ->
                    failedPages.add(download.page)
                    trackFinished()
                }
            )

            coordinator.enqueue(pages(15))

            runBlocking {
                withTimeout(5_000) { allFinished.await() }
            }
            assertEquals((1..15).filter { it != 7 }, transferredPages.sorted())
            assertEquals(listOf(7), failedPages)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun concurrentStatusUpdatesKeepEveryQueuedPage() {
        var backing = emptyList<DownloadInfo>()
        val store = DownloadListStore(
            readAll = { backing },
            writeAll = { backing = it }
        )
        store.enqueuePending(pages(15))

        runBlocking {
            coroutineScope {
                (1..15).map { page ->
                    async(Dispatchers.Default) {
                        repeat(200) { round ->
                            store.update("bvid_$page") { download ->
                                download.copy(fileSize = round.toLong())
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        assertEquals((1..15).toList(), store.all().map { it.page }.sorted())
    }

    @Test
    fun transferGateNeverExceedsConfiguredConcurrency() {
        val ownerScope = newScope()
        val allFinished = CompletableDeferred<Unit>()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val finished = AtomicInteger()
        val gate = DownloadTransferGate(maxConcurrent = 3)

        try {
            repeat(15) {
                ownerScope.launch {
                    gate.run {
                        val current = active.incrementAndGet()
                        peak.updateAndGet { previous -> maxOf(previous, current) }
                        delay(20)
                        active.decrementAndGet()
                        if (finished.incrementAndGet() == 15) {
                            allFinished.complete(Unit)
                        }
                    }
                }
            }

            runBlocking {
                withTimeout(5_000) { allFinished.await() }
            }
            assertEquals(3, peak.get())
            assertEquals(15, finished.get())
        } finally {
            ownerScope.cancel()
        }
    }

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun pages(count: Int): List<DownloadInfo> = (1..count).map { page ->
        DownloadInfo(
            id = "bvid_$page",
            bvid = "bvid",
            cid = page.toLong(),
            title = "video $page",
            cover = "",
            quality = 80,
            qualityDesc = "1080P",
            filePath = "/downloads/bvid_P$page.mp4",
            page = page,
            part = "part $page"
        )
    }

    private fun url(download: DownloadInfo): String = "https://example.com/${download.cid}.mp4"
}
