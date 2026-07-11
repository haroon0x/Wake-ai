package com.wake.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wake.app.WakeApp
import com.wake.app.data.AgentTask
import com.wake.app.data.STATUS_PROPOSED
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AgentViewModel : ViewModel() {

    val tasks: StateFlow<List<AgentTask>> = WakeApp.instance.agentTaskDao
        .feed(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val proposedCount: StateFlow<Int> = WakeApp.instance.agentTaskDao
        .countByStatus(STATUS_PROPOSED)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun approve(task: AgentTask) {
        viewModelScope.launch { WakeApp.instance.agent.approve(task.id) }
    }

    fun dismiss(task: AgentTask) {
        viewModelScope.launch { WakeApp.instance.agent.dismiss(task.id) }
    }
}
