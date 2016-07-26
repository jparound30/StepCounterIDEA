package com.github.jparound30.idea.plugin.stepcounter.action

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.HashMap
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil
import jp.sf.amateras.stepcounter.diffcount.DiffCounter
import jp.sf.amateras.stepcounter.diffcount.DiffCounterUtil
import jp.sf.amateras.stepcounter.diffcount.`object`.DiffFileResult
import jp.sf.amateras.stepcounter.diffcount.`object`.DiffFolderResult
import jp.sf.amateras.stepcounter.diffcount.`object`.DiffStatus
import java.io.File
import java.io.IOException

/**
 * Created by tabukinobuhiro on 2016/07/25.
 */
class StepCountAction : AnAction("Step Count") {
    companion object {
        val NORMAL_STEPS = 0
        val DIFF_STEPS   = 1
    }
    override fun actionPerformed(e: AnActionEvent?) {
        if (e == null) {
            return
        }
        val commitHashes = e.getData(VcsDataKeys.VCS_REVISION_NUMBERS)
        val mode = if (commitHashes?.size == 1) {
            NORMAL_STEPS
        } else {
            DIFF_STEPS
        }
        System.out.println("[BEFORE] : ${commitHashes?.get(0)?.asString()}")
        System.out.println("[AFTER ] : ${commitHashes?.last()?.asString()}")
        System.out.println("MODE = $mode")

        val projectFileDirectory = e.getData(DataKeys.PROJECT_FILE_DIRECTORY)
        System.out.println(projectFileDirectory.toString())

        val project = e.getRequiredData(CommonDataKeys.PROJECT)
        if (mode == NORMAL_STEPS) {
            Messages.showMessageDialog(project, "比較する２つのコミットを選択してください。", "Information", Messages.getInformationIcon())
            return
        }


        val vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(projectFileDirectory)


        // １つあるいは２つのソースを特定のフォルダにチェックアウト
        createTemporaryDirectories(vcsRoot!!, commitHashes?.last()!!, commitHashes?.first()!!)


        // チェックアウトしたフォルダに対して、ステップカウンタを実行

        // 2tu
        val targetPath = vcsRoot!!.path + "/.stepcount/new/"
        val comparePath = vcsRoot!!.path + "/.stepcount/old/"
        val results = count(targetPath, comparePath)
        val diffFileResults = DiffCounterUtil.convertToList(results)
        for (d in diffFileResults) {
            when(d.status) {
                DiffStatus.ADDED, DiffStatus.MODIFIED, DiffStatus.REMOVED -> {
                    System.out.println("${d.path} / ${d.name} / ${d.fileType} / ${d.addCount} / ${d.delCount} / ${d.status} / Total: ${d.addCount + d.delCount} ")
                }
                DiffStatus.NONE -> {
                    if (d.addCount != 0 || d.delCount != 0) {
                        System.out.println("${d.path} / ${d.name} / ${d.fileType} / ${d.addCount} / ${d.delCount} / ${d.status} / Total: ${d.addCount + d.delCount} ")
                    } else {
                        //System.out.println("[対象外] ${d.path} / ${d.name} / ${d.fileType} / ${d.addCount} / ${d.delCount} / ${d.status} / Total: ${d.addCount + d.delCount} ")
                    }
                }
                else -> {
                    //System.out.println("[対象外] ${d.path} / ${d.name} / ${d.fileType} / ${d.addCount} / ${d.delCount} / ${d.status} / Total: ${d.addCount + d.delCount} ")
                }
            }
        }
    }

    /**
     * 指定されたリソースの差分をカウントします。
     * @param targetPath 差分のカウント対象のルートパス
     * *
     * @param comparePath 差分の比較対象のルートパス
     * *
     * @return 差分カウントの結果
     */
    private fun count(targetPath: String, comparePath: String): DiffFolderResult {
        val oldRoot = File(comparePath)
        val newRoot = File(targetPath)

        return DiffCounter.count(oldRoot, newRoot)
    }

    private fun createTemporaryDirectories(projectRootVfs: VirtualFile,
                                           targetRevisionNumber: VcsRevisionNumber,
                                           compareRevisionNumber: VcsRevisionNumber) {
        val app = ApplicationManager.getApplication()

        app.runWriteAction {
            // ソースチェックアウト先を作成
            var tmpRoot: VirtualFile?
            tmpRoot = projectRootVfs.findChild(".stepcount")
            if (tmpRoot == null) {
                tmpRoot = projectRootVfs.createChildDirectory(this, ".stepcount")
            }

            val oldPathStr = tmpRoot.path + "/old"
            val newPathStr = tmpRoot.path + "/new"

            // すでにold/newがある場合は削除してcloneしなおす
            val oldPathFile = File(oldPathStr)
            if (oldPathFile.exists()) {
                FileUtil.delete(oldPathFile)
            }
            val newPathFile = File(newPathStr)
            if (newPathFile.exists()) {
                FileUtil.delete(newPathFile)
            }

            val originalRepository = projectRootVfs.findChild(".git")!!.path
            // 比較もとをclone
            clone(originalRepository, oldPathStr)
            checkoutSource(oldPathStr, compareRevisionNumber)

            //
            clone(originalRepository, newPathStr)
            checkoutSource(newPathStr, targetRevisionNumber)
        }

    }

    fun clone(originalRepository: String, destinationPath: String): String {
        val processBuilder = ProcessBuilder(
                "/usr/bin/git", "clone", originalRepository, destinationPath)

        val handler: BaseOSProcessHandler
        try {
            handler = BaseOSProcessHandler(processBuilder.start(), "git clone", null)
        } catch (e: IOException) {
            throw e
        }

        val builder = StringBuilder()
        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent?, outputType: Key<*>?) {
                builder.append(event!!.text)
            }
        })
        handler.startNotify()
        handler.waitFor()
        System.out.println(builder.toString())
        System.out.println(handler.process.exitValue())
        return builder.toString()
    }

    private fun checkoutSource(destinationPath: String, revision: VcsRevisionNumber): String {
        val processBuilder = ProcessBuilder(
                "/usr/bin/git", "checkout", revision.asString())
        processBuilder.directory(File(destinationPath))
        val handler: BaseOSProcessHandler
        try {
            handler = BaseOSProcessHandler(processBuilder.start(), "git checkout", null)
        } catch (e: IOException) {
            throw e
        }

        val builder = StringBuilder()
        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent?, outputType: Key<*>?) {
                builder.append(event!!.text)
            }
        })
        handler.startNotify()
        handler.waitFor()
        System.out.println(builder.toString())
        System.out.println(handler.process.exitValue())
        return builder.toString()
    }

}