package com.github.jparound30.idea.plugin.stepcounter.action

import com.github.jparound30.idea.plugin.stepcounter.ui.StepDiffView
import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.openapi.ui.WindowWrapperBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.awt.RelativePoint
import jp.sf.amateras.stepcounter.diffcount.DiffCounter
import jp.sf.amateras.stepcounter.diffcount.DiffCounterUtil
import jp.sf.amateras.stepcounter.diffcount.`object`.DiffFileResult
import jp.sf.amateras.stepcounter.diffcount.`object`.DiffFolderResult
import jp.sf.amateras.stepcounter.diffcount.`object`.DiffStatus
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.*
import javax.swing.event.TableModelListener
import javax.swing.table.TableModel

/**
 * @author jparound30
 */
class StepCountAction : AnAction("Step Count!") {
    companion object {
        val NORMAL_STEPS = 0
        val DIFF_STEPS   = 1

        val workDirSuffix = ".stepcount"
        val oldDirStr = "old"
        val newDirStr = "new"

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


        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Counting steps...", false) {
            override fun run(progressIndicator: ProgressIndicator) {
                // １つあるいは２つのソースを特定のフォルダにチェックアウト
                progressIndicator.fraction = 0.10
                progressIndicator.text = "Checkout revision"
                val targetRevision = commitHashes?.first()
                val compareRevision = commitHashes?.last()
                this@StepCountAction.createTemporaryDirectories(project, vcsRoot!!, commitHashes?.first()!!, commitHashes?.last()!!)

                // チェックアウトしたフォルダに対して、ステップカウンタを実行
                progressIndicator.fraction = 0.75
                progressIndicator.text = "Counting"
                val targetPath = vcsRoot.path + File.separator + workDirSuffix + File.separator + newDirStr
                val comparePath = vcsRoot.path + File.separator + workDirSuffix + File.separator + oldDirStr
                val results = this@StepCountAction.count(targetPath, comparePath)
                val diffFileResults = DiffCounterUtil.convertToList(results)
                val filteredDiffFileResults = ArrayList<DiffFileResult>()
                for (d in diffFileResults) {
                    when (d.status) {
                        DiffStatus.ADDED, DiffStatus.MODIFIED, DiffStatus.REMOVED -> {
                            System.out.println("${d.path} / ${d.name} / ${d.fileType} / ${d.addCount} / ${d.delCount} / ${d.status} / Total: ${d.addCount + d.delCount} ")
                            filteredDiffFileResults.add(d)
                        }
                        DiffStatus.NONE -> {
                            if (d.addCount != 0 || d.delCount != 0) {
                                System.out.println("${d.path} / ${d.name} / ${d.fileType} / ${d.addCount} / ${d.delCount} / ${d.status} / Total: ${d.addCount + d.delCount} ")
                                filteredDiffFileResults.add(d)
                            } else {
                                //System.out.println("[対象外] ${d.path} / ${d.name} / ${d.fileType} / ${d.addCount} / ${d.delCount} / ${d.status} / Total: ${d.addCount + d.delCount} ")
                            }
                        }
                        else -> {
                            //System.out.println("[対象外] ${d.path} / ${d.name} / ${d.fileType} / ${d.addCount} / ${d.delCount} / ${d.status} / Total: ${d.addCount + d.delCount} ")
                            //filteredDiffFileResults.add(d)
                        }
                    }
                }
                if (filteredDiffFileResults.size != 0) {
                    ApplicationManager.getApplication().invokeLater {

                        val dataContext = SimpleDataContext.getProjectContext(project)

                        val rootComponent = StepDiffView(filteredDiffFileResults)

                        val windowWrapperBuilder = WindowWrapperBuilder(WindowWrapper.Mode.FRAME, rootComponent.rootPanel!!)
                        val windowWrapper = windowWrapperBuilder
                                .setTitle("Step Count")
                                .setProject(project)
                                .setPreferredFocusedComponent(rootComponent.rootPanel)
                                .setDimensionServiceKey(StepCountAction::class.java.name)
                                .build()
                        windowWrapper.show()

                        rootComponent.setCancelListener(
                                ActionListener {
                                    System.out.println("onCancel")
                                    windowWrapper.close()
                                })
                        rootComponent.setSaveListener(
                                ActionListener {
                                    System.out.println("onSave")
                                    val file = File(vcsRoot.path + File.separator + workDirSuffix + File.separator + "result_${compareRevision?.asString()}_${targetRevision?.asString()}.csv")
                                    val fileOutputStream = FileOutputStream(file)
                                    val writer = fileOutputStream.writer(Charset.forName("UTF-8"))
                                    val header = "path,name,fileType,d.status,addCount,delCount,total\n"
                                    writer.write(header)
                                    filteredDiffFileResults.forEach { d ->
                                        val csvRow = "${d.path},${d.name},${d.fileType},${d.status},${d.addCount},${d.delCount},${d.addCount + d.delCount}\n"
                                        writer.write(csvRow)
                                    }
                                    writer.flush()
                                    writer.close()
                                    fileOutputStream.close()
                                }
                        )
                    }
                } else {
                    Messages.showMessageDialog(project, "差分はありません。", "Information", Messages.getInformationIcon())
                }
            }
        })
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

    private fun createTemporaryDirectories(project: Project,
                                           projectRootVfs: VirtualFile,
                                           targetRevisionNumber: VcsRevisionNumber,
                                           compareRevisionNumber: VcsRevisionNumber) {
        // ソースチェックアウト先を作成
        var tmpRoot: VirtualFile?
        tmpRoot = projectRootVfs.findChild(workDirSuffix)
        if (tmpRoot == null) {
            tmpRoot = projectRootVfs.createChildDirectory(this, workDirSuffix)
        }

        val oldPathStr = tmpRoot.path + File.separator + oldDirStr
        val newPathStr = tmpRoot.path + File.separator + newDirStr

        // すでにold/newがある場合は削除してcloneしなおす
        val oldPathFile = File(oldPathStr)
        if (oldPathFile.exists()) {
            FileUtil.delete(oldPathFile)
        }
        val newPathFile = File(newPathStr)
        if (newPathFile.exists()) {
            FileUtil.delete(newPathFile)
        }

//                progressIndicator.fraction = 0.3

        val originalRepository = projectRootVfs.findChild(".git")!!.path

//                progressIndicator.fraction = 0.6
        // 比較もとをclone
        clone(originalRepository, oldPathStr)
        checkoutSource(oldPathStr, compareRevisionNumber)

        //
        clone(originalRepository, newPathStr)
        checkoutSource(newPathStr, targetRevisionNumber)

    }

    private fun clone(originalRepository: String, destinationPath: String): String {
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
//        System.out.println(builder.toString())
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
//        System.out.println(builder.toString())
        System.out.println(handler.process.exitValue())
        return builder.toString()
    }

    class DiffTableMode(val diffFileResult: List<DiffFileResult>) : TableModel {
        companion object {
            val columnNames = arrayOf(
                    "Path",
                    "Add",
                    "Del",
                    "Add + Del"
            )
        }
        override fun getRowCount(): Int {
            return diffFileResult.size
        }

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return String::class.java
        }

        override fun addTableModelListener(l: TableModelListener?) {
            //
        }

        override fun getColumnName(columnIndex: Int): String {
            return  columnNames[columnIndex]
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            return false
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            //
        }

        override fun getColumnCount(): Int {
            return columnNames.size
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val t = diffFileResult[rowIndex]
            return when(columnIndex) {
                0 -> t.path
                1 -> t.addCount
                2 -> t.delCount
                3 -> t.addCount + t.delCount
                else -> throw IllegalArgumentException("columnIndex = $columnIndex")
            }
        }

        override fun removeTableModelListener(l: TableModelListener?) {
            //
        }

    }
}