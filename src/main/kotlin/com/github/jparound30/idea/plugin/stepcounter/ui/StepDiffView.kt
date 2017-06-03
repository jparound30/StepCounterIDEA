package com.github.jparound30.idea.plugin.stepcounter.ui

import com.github.jparound30.idea.plugin.stepcounter.action.StepCountAction
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.layout.panel
import com.intellij.ui.table.JBTable
import jp.sf.amateras.stepcounter.diffcount.`object`.DiffFileResult

import javax.swing.*
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener

/**
 * @author jparound30
 */
class StepDiffView(data: List<DiffFileResult>) {
    private var tableView: JBTable? = null
    private var btnCancel: JButton
    private var btnSave: JButton
    var rootPanel: JComponent? = null
        private set

    private val tableModel: StepCountAction.DiffTableMode = StepCountAction.DiffTableMode(data)

    private var _cancelListener: ActionListener? = null
    fun setCancelListener(listener: ActionListener)
    {
        _cancelListener = listener
    }

    private fun cancel(event: ActionEvent) {
        if (_cancelListener != null) {
            _cancelListener!!.actionPerformed(event)
        }
    }

    private var _saveListener: ActionListener? = null
    fun setSaveListener(listener: ActionListener) {
        _saveListener = listener
    }

    private fun save(event: ActionEvent) {
        if (_saveListener != null) {
            _saveListener!!.actionPerformed(event)
        }
    }

    init {
        tableView = JBTable()

        tableView!!.model = tableModel
        tableView!!.columnModel.getColumn(1).maxWidth = 75
        tableView!!.columnModel.getColumn(2).maxWidth = 75
        tableView!!.columnModel.getColumn(3).maxWidth = 150
        tableView!!.preferredScrollableViewportSize = Dimension(1200, 550)
        tableView!!.putClientProperty("html.disable", java.lang.Boolean.FALSE)
        val scroll = JBScrollPane()
        scroll.add(tableView)
        scroll.setViewportView(tableView)

        btnCancel = JButton("Cancel")
        btnCancel.addActionListener { event -> cancel(event)}

        btnSave = JButton("Save")
        btnSave.addActionListener { event -> save(event) }

        rootPanel = panel {
            row {
                scroll()
            }
            row {
                btnCancel()
                btnSave()
            }
        }
    }
}
