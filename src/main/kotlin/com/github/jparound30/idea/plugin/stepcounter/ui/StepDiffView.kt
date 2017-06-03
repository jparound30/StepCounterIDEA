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
    private var Cancel: JButton? = null
    private var Save: JButton? = null
    var rootPanel: JComponent? = null
        private set

    private val tableModel: StepCountAction.DiffTableMode

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

    private var _okListener: ActionListener? = null
    fun setOkListener(listener: ActionListener) {
        _okListener = listener
    }

    private fun ok(event: ActionEvent) {
        if (_okListener != null) {
            _okListener!!.actionPerformed(event)
        }
    }

    init {
        tableModel = StepCountAction.DiffTableMode(data)
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

        val btnCancel = JButton("Cancel")
        btnCancel.addActionListener { event -> cancel(event)}

        val btnOk = JButton("OK")
        btnOk.addActionListener { event -> ok(event) }

        rootPanel = panel {
            row {
                scroll()
            }
            row {
                btnCancel()
                btnOk()
            }
        }
    }

    private fun createUIComponents() {
        // TODO: place custom component creation code here
    }
}
