package metrik.infrastructure.encryption

import metrik.project.domain.model.Build
import metrik.project.domain.model.Pipeline
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest
internal class DatabaseEncryptionAspectTest {
    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @MockBean
    private lateinit var encryptionService: AESEncryptionService

    @BeforeEach
    internal fun setUp() {
        `when`(encryptionService.encrypt(anyString())).thenReturn("encrypted")
        `when`(encryptionService.decrypt(anyString())).thenReturn("decrypted")

        val collectionName = "pipeline"
        mongoTemplate.dropCollection(collectionName)
    }

    @Test
    internal fun `should encrypt data before call save() method in mongoTemplate for pipeline data`() {
        val username = "mock-username"
        val credential = "mock-credential"

        val pipelineSaved = mongoTemplate.save(Pipeline(username = username, credential = credential))

        verify(encryptionService, times(1)).encrypt(username)
        verify(encryptionService, times(1)).encrypt(credential)
        assertThat(pipelineSaved.username).isEqualTo("encrypted")
        assertThat(pipelineSaved.credential).isEqualTo("encrypted")
    }

    @Test
    internal fun `should not encrypt data before call save() method in mongoTemplate for non pipeline data`() {
        mongoTemplate.save(Build())

        verify(encryptionService, never()).encrypt(anyString())
        verify(encryptionService, never()).encrypt(anyString())
    }

    @Test
    internal fun `should encrypt data before call insert() method in mongoTemplate for pipeline data`() {
        val username = "mock-username"
        val credential = "mock-credential"

        val pipelinesSaved = mongoTemplate.insert(
            mutableListOf(Pipeline(username = username, credential = credential)),
            Pipeline::class.java
        ).toList()

        verify(encryptionService, times(1)).encrypt(username)
        verify(encryptionService, times(1)).encrypt(credential)
        assertThat(pipelinesSaved[0].username).isEqualTo("encrypted")
        assertThat(pipelinesSaved[0].credential).isEqualTo("encrypted")
    }

    @Test
    internal fun `should not encrypt data before call insert() method in mongoTemplate for non pipeline data`() {
        mongoTemplate.insert(
            mutableListOf(Build()),
            Build::class.java
        ).toList()

        verify(encryptionService, never()).encrypt(anyString())
        verify(encryptionService, never()).encrypt(anyString())
    }

    @Test
    internal fun `should decrypt data after call find() method in mongoTemplate for pipeline data only`() {
        val username = "mock-username"
        val credential = "mock-credential"
        val projectId = "fake-project"

        mongoTemplate.save(Pipeline(projectId = projectId, username = username, credential = credential))

        val pipelinesRetrieved = mongoTemplate.find(
            Query().addCriteria(Criteria.where("projectId").isEqualTo(projectId)),
            Pipeline::class.java
        )

        verify(encryptionService, times(2)).decrypt("encrypted")
        assertThat(pipelinesRetrieved[0].username).isEqualTo("decrypted")
        assertThat(pipelinesRetrieved[0].credential).isEqualTo("decrypted")
    }

    @Test
    internal fun `should not decrypt data after call find() method in mongoTemplate for non pipeline data`() {
        mongoTemplate.save(Build(pipelineId = "1"))

        mongoTemplate.find(
            Query().addCriteria(Criteria.where("pipelineId").isEqualTo("1")),
            Build::class.java
        )

        verify(encryptionService, times(0)).decrypt(anyString())
    }

    @Test
    internal fun `should decrypt data after call findOne() method in mongoTemplate for pipeline data only`() {
        val username = "mock-username"
        val credential = "mock-credential"
        val projectId = "fake-project"

        mongoTemplate.save(Pipeline(projectId = projectId, username = username, credential = credential))

        val pipelineRetrieved = mongoTemplate.findOne(
            Query().addCriteria(Criteria.where("projectId").isEqualTo(projectId)),
            Pipeline::class.java
        )

        verify(encryptionService, times(2)).decrypt("encrypted")
        assertThat(pipelineRetrieved!!.username).isEqualTo("decrypted")
        assertThat(pipelineRetrieved.credential).isEqualTo("decrypted")
    }

    @Test
    internal fun `should not decrypt data after call findOne() method in mongoTemplate for non pipeline data`() {
        mongoTemplate.save(Build(pipelineId = "1"))

        mongoTemplate.findOne(
            Query().addCriteria(Criteria.where("projectId").isEqualTo("1")),
            Pipeline::class.java
        )

        verify(encryptionService, times(0)).decrypt(anyString())
    }

    @Test
    internal fun `should not decrypt data after call findOne() method in mongoTemplate when it returns null`() {
        mongoTemplate.findOne(
            Query().addCriteria(Criteria.where("pipelineId").isEqualTo("1")),
            Pipeline::class.java
        )

        verify(encryptionService, times(0)).decrypt(anyString())
    }
}
