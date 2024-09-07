package dev.voqal.assistant.tool.system

import com.aallam.openai.api.chat.Tool
import com.aallam.openai.api.core.Parameters
import com.intellij.history.Label
import com.intellij.history.LocalHistoryAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import dev.voqal.assistant.VoqalDirective
import dev.voqal.assistant.focus.DetectedIntent
import dev.voqal.assistant.focus.SpokenTranscript
import dev.voqal.assistant.tool.VoqalTool
import dev.voqal.assistant.tool.ide.UnselectTool
import dev.voqal.assistant.tool.text.EditTextTool.Companion.VOQAL_HIGHLIGHTERS
import dev.voqal.services.VoqalMemoryService
import dev.voqal.services.VoqalStatusService
import dev.voqal.services.VoqalToolService
import dev.voqal.services.getVoqalLogger
import dev.voqal.status.VoqalStatus
import io.vertx.core.json.JsonObject

class CancelTool : VoqalTool() {

    companion object {
        const val NAME = "cancel"
    }

    override val name = NAME

    override suspend fun actionPerformed(args: JsonObject, directive: VoqalDirective) {
        val project = directive.project
        val log = project.getVoqalLogger(this::class)
        log.info("Triggering cancel")

        val statusService = project.service<VoqalStatusService>()
        if (statusService.getStatus() == VoqalStatus.IDLE) {
            val updateText = args.getBoolean("updateText", true)
            if (updateText) {
                //todo: try unselect before saying cancel unavailable
                project.service<VoqalStatusService>().updateText("Cancel unavailable")
            }
            return
        }

        val memoryService = project.service<VoqalMemoryService>()
        val visibleRangeHighlighter = memoryService.getUserData("visibleRangeHighlighter")
        val affectedFiles = memoryService.getUserData("affectedFiles")
        val action = memoryService.getUserData("voqal.edit.action") as LocalHistoryAction?
        val label = memoryService.getUserData("voqal.edit") as Label?
        val memory = memoryService.getCurrentMemory()
        memoryService.resetMemory()

        //do anything considered cancelling
        val editor = directive.ide.editor
        if (statusService.getStatus() == VoqalStatus.SEARCHING) {
            //nop
        } else if (statusService.getStatus() == VoqalStatus.EDITING) {
            log.info("Reverting changes to: ${memory.id}")
            action?.finish()
            label?.revert(project, editor!!.virtualFile)
            (affectedFiles as? List<*>)?.let { it.forEach { f -> label?.revert(project, (f as PsiFile).virtualFile) } }
        } else {
            log.warn("Invalid status: ${statusService.getStatus()}")
        }

        //ensure highlighters removed
        if (visibleRangeHighlighter is RangeHighlighter) {
            editor?.markupModel?.removeHighlighter(visibleRangeHighlighter)
        }
        editor?.getUserData(VOQAL_HIGHLIGHTERS)?.forEach {
            editor.markupModel.removeHighlighter(it)
        }
        editor?.putUserData(VOQAL_HIGHLIGHTERS, emptyList())
        project.service<VoqalToolService>().blindExecute(UnselectTool(), JsonObject().put("updateText", false))

        //reset status
        project.service<VoqalStatusService>().updateText("Cancelled")

        if (statusService.getStatus() !in setOf(VoqalStatus.DISABLED, VoqalStatus.IDLE)) {
            statusService.update(VoqalStatus.IDLE)
        }
    }

    override suspend fun getTranscriptIntent(project: Project, transcript: SpokenTranscript): DetectedIntent? {
        return attemptIntentExtract(transcript.cleanTranscript)?.let { (intent, args) ->
            DetectedIntent(intent, args, transcript, this)
        }
    }

    internal fun attemptIntentExtract(rawString: String): Pair<String, Map<String, String>>? {
        return if (rawString == "cancel") {
            Pair(NAME, mapOf())
        } else {
            null
        }
    }

    override fun isVisible(directive: VoqalDirective): Boolean {
        return directive.assistant.promptSettings?.promptName?.lowercase() != "idle mode"
    }

    override fun asTool(directive: VoqalDirective) = Tool.function(
        name = NAME,
        description = "Cancel the current action",
        parameters = Parameters.Empty
    )
}
