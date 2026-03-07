package application

import io.mockk.*
import io.specmatic.stub.stateful.StatefulHttpStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.BindException
import java.util.concurrent.CountDownLatch

class VirtualServiceCommandTest {

    @AfterEach
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `shutdown hook should close the server and count down the latch`() {
        val command = VirtualServiceCommand()

        val mockServer = mockk<StatefulHttpStub>(relaxed = true)
        val serverField = VirtualServiceCommand::class.java.getDeclaredField("server")
        serverField.isAccessible = true
        serverField.set(command, mockServer)

        val latchField = VirtualServiceCommand::class.java.getDeclaredField("latch")
        latchField.isAccessible = true
        val latch = latchField.get(command) as CountDownLatch

        assertThat(latch.count).isEqualTo(1)

        // Simulate the shutdown hook behavior
        latch.countDown()
        mockServer.close()

        assertThat(latch.count).isEqualTo(0)
        verify(exactly = 1) { mockServer.close() }
    }

    @Test
    fun `call should return exit code 1 when server startup fails due to port conflict`() {
        mockkStatic("io.specmatic.core.utilities.Utilities")
        every {
            io.specmatic.core.utilities.contractStubPaths(any(), any())
        } returns emptyList()

        every {
            io.specmatic.core.utilities.exitIfAnyDoNotExist(any(), any())
        } just Runs

        every {
            io.specmatic.core.utilities.contractFilePathsFrom(any(), any(), any(), any())
        } returns emptyList()

        // Mock StubLoaderEngine to throw BindException simulating port conflict
        mockkConstructor(StubLoaderEngine::class)
        every {
            anyConstructed<StubLoaderEngine>().loadStubs(any(), any(), any(), any())
        } throws BindException("Address already in use")

        val command = VirtualServiceCommand()
        command.host = "127.0.0.1"
        command.port = 9999

        val exitCode = command.call()

        assertThat(exitCode).isEqualTo(1)
    }
}
