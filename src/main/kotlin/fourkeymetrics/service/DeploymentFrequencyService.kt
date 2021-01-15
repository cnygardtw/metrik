package fourkeymetrics.service

import fourkeymetrics.model.Build
import fourkeymetrics.model.BuildStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class DeploymentFrequencyService {
    @Autowired
    private lateinit var pipelineService: PipelineService

    fun getDeploymentCount(pipelineID: String, targetStage: String, startTime: Long, endTime: Long): Int {
        val allBuilds = pipelineService.getAllBuilds(pipelineID).sortedBy { it.timestamp }

        var validDeploymentCount = 0
        for (index in allBuilds.indices) {
            val currentBuild = allBuilds[index]
            val prevBuild = if (index == 0) {
                null
            } else {
                allBuilds[index - 1]
            }

            if (isInvalidBuild(currentBuild, startTime, endTime, targetStage, prevBuild)) {
                continue
            }

            validDeploymentCount++
        }

        return validDeploymentCount
    }

    private fun isInvalidBuild(currentBuild: Build, startTime: Long, endTime: Long, targetStage: String, prevBuild: Build?): Boolean {
        return !isWithinTimeRange(currentBuild, startTime, endTime) ||
                !isBuildSuccess(currentBuild) ||
                !isTargetStageSuccess(currentBuild, targetStage) ||
                !isEffectiveDeployment(prevBuild, currentBuild)
    }

    private fun isTargetStageSuccess(build: Build, targetStage: String) =
            build.stages.find { stage -> stage.name == targetStage }?.status == BuildStatus.SUCCESS

    private fun isBuildSuccess(build: Build) = build.status == BuildStatus.SUCCESS

    private fun isWithinTimeRange(build: Build, startTime: Long, endTime: Long) =
            build.timestamp in startTime..endTime

    private fun isEffectiveDeployment(previousBuild: Build?, currentBuild: Build) =
            if (previousBuild == null) {
                true
            } else {
                previousBuild.commits != currentBuild.commits
            }
}
