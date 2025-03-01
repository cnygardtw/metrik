package metrik.project.rest.applicationservice

import metrik.MockitoHelper.anyObject
import metrik.project.domain.model.Build
import metrik.exception.ApplicationException
import metrik.project.rest.vo.response.SyncProgress
import metrik.project.domain.model.Pipeline
import metrik.project.domain.model.PipelineType
import metrik.project.domain.model.Project
import metrik.project.domain.repository.PipelineRepository
import metrik.project.domain.repository.ProjectRepository
import metrik.project.domain.service.PipelineService
import metrik.project.domain.service.factory.PipelineServiceFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
internal class SynchronizationApplicationServiceTest {
    @Mock
    private lateinit var pipelineServiceMock: PipelineService

    @Mock
    private lateinit var pipelineServiceFactory: PipelineServiceFactory

    @Mock
    private lateinit var projectRepository: ProjectRepository

    @Mock
    private lateinit var pipelineRepository: PipelineRepository

    @InjectMocks
    private lateinit var synchronizationApplicationService: SynchronizationApplicationService

    private val emitCbCaptor = argumentCaptor<(SyncProgress) -> Unit>()

    @BeforeEach
    fun setup() {
        Mockito.lenient().`when`(pipelineServiceFactory.getService(anyObject())).thenReturn(pipelineServiceMock)
    }

    @Test
    internal fun `should sync builds when there is no previous update`() {
        val projectId = "fake-project-id"
        val pipelineId = "fake-pipeline-id"
        val pipeline = Pipeline(id = pipelineId, type = PipelineType.BAMBOO)
        val builds = listOf(Build())

        `when`(projectRepository.findById(anyString())).thenReturn(Project(projectId))
        `when`(pipelineRepository.findByProjectId(projectId)).thenReturn(listOf(pipeline))
        `when`(pipelineServiceMock.syncBuildsProgressively(anyObject(), anyObject())).thenReturn(builds)

        val updatedTimestamp = synchronizationApplicationService.synchronize(projectId)

        verify(pipelineServiceMock, times(1)).syncBuildsProgressively(eq(pipeline), emitCbCaptor.capture())
        verify(projectRepository, times(1)).updateSynchronizationTime(anyString(), anyLong())

        assertThat(updatedTimestamp).isNotNull
    }

    @Test
    internal fun `should save builds from 2 weeks ago of previous update to now`() {
        val projectId = "fake-project-id"
        val pipelineId = "fake-pipeline-id"
        val pipeline = Pipeline(id = pipelineId, type = PipelineType.BAMBOO)
        val builds = listOf(Build())
        val lastSyncTimestamp = 1610668800000

        `when`(projectRepository.findById(projectId)).thenReturn(Project(projectId, "name", lastSyncTimestamp))
        `when`(pipelineRepository.findByProjectId(projectId)).thenReturn(listOf(pipeline))
        `when`(projectRepository.updateSynchronizationTime(anyString(), anyLong())).thenReturn(lastSyncTimestamp + 1)
        `when`(pipelineServiceMock.syncBuildsProgressively(anyObject(), anyObject())).thenReturn(builds)

        val updateTimestamp = synchronizationApplicationService.synchronize(projectId)

        verify(pipelineServiceMock, times(1)).syncBuildsProgressively(eq(pipeline), emitCbCaptor.capture())
        verify(projectRepository, times(1)).updateSynchronizationTime(anyString(), anyLong())

        assertThat(updateTimestamp).isGreaterThan(lastSyncTimestamp)
    }

    @Test
    internal fun `should not save any builds if fetch data from pipeline failed`() {
        val projectId = "fake-project-id"
        val pipelineId = "fake-pipeline-id"
        val pipeline = Pipeline(id = pipelineId, type = PipelineType.BAMBOO)
        val lastSyncTimestamp = 1610668800000

        `when`(projectRepository.findById(projectId)).thenReturn(Project(projectId, "name", lastSyncTimestamp))
        `when`(pipelineRepository.findByProjectId(projectId)).thenReturn(listOf(pipeline))
        `when`(projectRepository.updateSynchronizationTime(anyString(), anyLong())).thenReturn(lastSyncTimestamp + 1)
        `when`(pipelineServiceMock.syncBuildsProgressively(anyObject(), anyObject())).thenThrow(RuntimeException())

        assertThrows<ApplicationException> { synchronizationApplicationService.synchronize(projectId) }
    }

    @Test
    internal fun `should get last synchronization time`() {
        val projectId = "fake-project-id"
        val lastSyncTimestamp = 1610668800000

        `when`(projectRepository.findById(projectId)).thenReturn(Project(projectId, "name", lastSyncTimestamp))

        val updatedTimestamp = synchronizationApplicationService.getLastSyncTimestamp(projectId)

        assertThat(updatedTimestamp).isEqualTo(lastSyncTimestamp)
    }

    @Test
    internal fun `should sync builds for Bamboo when there is no previous update`() {
        val projectId = "fake-project-id"
        val pipelineId = "fake-pipeline-id"
        val pipeline = Pipeline(id = pipelineId, type = PipelineType.BAMBOO)
        val builds = listOf(Build())

        `when`(projectRepository.findById(anyString())).thenReturn(Project(projectId))
        `when`(pipelineRepository.findByProjectId(projectId)).thenReturn(
            listOf(
                Pipeline(
                    id = pipelineId,
                    type = PipelineType.BAMBOO
                )
            )
        )
        `when`(pipelineServiceMock.syncBuildsProgressively(anyObject(), anyObject())).thenReturn(builds)

        val updatedTimestamp = synchronizationApplicationService.synchronize(projectId)

        verify(pipelineServiceMock, times(1)).syncBuildsProgressively(eq(pipeline), emitCbCaptor.capture())
        verify(projectRepository, times(1)).updateSynchronizationTime(anyString(), anyLong())

        assertThat(updatedTimestamp).isNotNull
    }

    @Test
    internal fun `should sync builds with progress emit callback passed in if callback function provided`() {
        val projectId = "fake-project-id"
        val pipelineId = "fake-pipeline-id"
        val pipeline = Pipeline(id = pipelineId, type = PipelineType.BAMBOO)
        val builds = listOf(Build())
        val mockEmitCb = mock<(SyncProgress) -> Unit>()

        `when`(projectRepository.findById(anyString())).thenReturn(Project(projectId))
        `when`(pipelineRepository.findByProjectId(projectId)).thenReturn(
            listOf(
                Pipeline(
                    id = pipelineId,
                    type = PipelineType.BAMBOO
                )
            )
        )
        `when`(pipelineServiceMock.syncBuildsProgressively(anyObject(), anyObject())).thenReturn(builds)

        val updatedTimestamp = synchronizationApplicationService.synchronize(projectId, mockEmitCb)

        verify(pipelineServiceMock, times(1)).syncBuildsProgressively(eq(pipeline), emitCbCaptor.capture())
        verify(projectRepository, times(1)).updateSynchronizationTime(anyString(), anyLong())
        val progress = SyncProgress(pipelineId, "pipeline name", 1, 1)
        emitCbCaptor.firstValue.invoke(progress)
        verify(mockEmitCb, times(1)).invoke(progress)
        assertThat(updatedTimestamp).isNotNull
    }
}