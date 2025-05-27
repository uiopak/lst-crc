package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.GitService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class BranchSelectionDialogWrapper(
    private val project: Project,
    private val gitService: GitService,
    private val onBranchSelected: (branchName: String) -> Unit
) : DialogWrapper(project, true) {

    private val searchTextField = SearchTextField(false)
    private var listPopup: JBPopup? = null
    private val allBranches = gitService.getAllBranches().sorted()
    private val filteredListModel = DefaultListModel<String>()

    init {
        title = "Select Branch to Compare with HEAD"
        init()
        searchTextField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filterAndShowPopup()
            override fun removeUpdate(e: DocumentEvent?) = filterAndShowPopup()
            override fun changedUpdate(e: DocumentEvent?) = filterAndShowPopup()
        })
        SwingUtilities.invokeLater { filterAndShowPopup() }
    }

    private fun filterAndShowPopup() {
        val searchText = searchTextField.text.trim()
        filteredListModel.clear()
        val sourceList = if (searchText.isEmpty()) allBranches else allBranches.filter { it.contains(searchText, ignoreCase = true) }
        sourceList.forEach { filteredListModel.addElement(it) }

        if (listPopup?.isDisposed == false) {
            listPopup?.cancel()
        }

        if (filteredListModel.isEmpty && searchText.isNotEmpty()) {
            return
        }

        if (filteredListModel.size > 0) {
            val jbList = JBList(filteredListModel)
            jbList.visibleRowCount = JBUI.scale(10).coerceAtMost(filteredListModel.size).coerceAtLeast(1)

            listPopup = JBPopupFactory.getInstance().createListPopupBuilder(jbList)
                .setMovable(false)
                .setResizable(false)
                .setRequestFocus(false)
                .setItemChoosenCallback {
                    jbList.selectedValue?.let {
                        onBranchSelected(it)
                        close(OK_EXIT_CODE)
                    }
                }
                .createPopup()

            jbList.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) {
                        jbList.selectedValue?.let {
                            onBranchSelected(it)
                            close(OK_EXIT_CODE)
                            e.consume()
                        }
                    }
                }
            })

            if (searchTextField.isShowing) {
                // Check if searchTextField has focus, or if focused component is part of listPopup
                val focusedComponent = SwingUtilities.getWindowAncestor(searchTextField)?.mostRecentFocusOwner
                if (focusedComponent == searchTextField || listPopup?.isFocused == true) {
                    listPopup?.showUnderneathOf(searchTextField)
                }
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(5)))
        panel.add(JBLabel("Search for branch to compare with current HEAD:"), BorderLayout.NORTH)
        panel.add(searchTextField, BorderLayout.CENTER)
        panel.preferredSize = JBUI.size(450, 60)
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent? = searchTextField

    override fun doOKAction() {
        if (listPopup?.isVisible == true) {
            (listPopup?.content as? JBList<*>)?.selectedValue?.let {
                onBranchSelected(it as String)
                super.doOKAction()
                return
            }
        }
        if (!filteredListModel.isEmpty) {
            onBranchSelected(filteredListModel.getElementAt(0))
            super.doOKAction()
        } else {
            if (searchTextField.text.isNotBlank() && filteredListModel.isEmpty) {
                Messages.showWarningDialog(project, "No branch found matching '${searchTextField.text}'.", "Branch Not Found")
                // Don't close the dialog, let user correct.
                return
            }
            super.doCancelAction()
        }
    }

    override fun dispose() {
        listPopup?.cancel()
        super.dispose()
    }
}
