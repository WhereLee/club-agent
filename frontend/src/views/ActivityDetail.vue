<template>
  <div class="page" v-loading="loading">
    <div class="header">
      <el-button link @click="router.push(`/clubs/${clubId}/activities`)">← 活动列表</el-button>
      <h2>活动详情</h2>
      <el-tag :type="statusType(activity?.status)" size="large">{{ statusText(activity?.status) }}</el-tag>
    </div>

    <!-- 面板加载失败提示（非静默） -->
    <el-alert v-if="panelError" :title="panelError" type="error" show-icon closable
      @close="panelError = ''" class="block" />

    <!-- 基本信息 -->
    <el-card v-if="activity" class="block">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="计划时间">{{ activity.plannedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地点">{{ activity.plannedLocation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发起人">{{ activity.creatorName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(activity.status) }}</el-descriptions-item>
        <el-descriptions-item label="活动内容" :span="2">{{ activity.content || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="activity.cancelReason" label="取消理由" :span="2">
          <span class="danger">{{ activity.cancelReason }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 发起人操作区（状态机推进） -->
    <el-card v-if="isOwner && activity" class="block">
      <template #header>发起人操作</template>
      <div class="actions">
        <el-button v-if="activity.status === 1" type="primary" @click="surveyDialog = true">发布问卷</el-button>
        <el-button v-if="activity.status === 2" @click="loadResults">查看问卷结果</el-button>
        <el-button v-if="activity.status === 2" type="primary" @click="doCloseSurvey">结束问卷，开启讨论</el-button>
        <el-button v-if="activity.status === 3 && !activity.discussionClosedAt" type="primary" @click="doEndDiscussion">结束讨论（解锁文件撰写）</el-button>
        <el-button v-if="activity.status === 3" type="primary" @click="scrollToFile">撰写正式文件</el-button>
        <el-button v-if="activity.status === 3" type="primary" plain @click="openAiPanel">AI 协助起草</el-button>
        <el-button v-if="activity.status === 4" type="primary" @click="signupDialog = true">开始报名</el-button>
        <el-button v-if="activity.status === 5" type="primary" @click="execDialog = true">开始执行</el-button>
        <el-button v-if="activity.status === 6" type="primary" @click="doCompleteExecution">结束执行，开放留痕</el-button>
        <el-button v-if="activity.status === 7" type="primary" @click="doCloseRecords">关闭留痕，进入总结</el-button>
        <el-button v-if="activity.status === 8" type="primary" :loading="archiving" @click="doArchive">归档活动</el-button>
        <el-button v-if="activity.status >= 1 && activity.status <= 4" type="danger" plain @click="cancelDialog = true">取消活动</el-button>
      </div>
    </el-card>

    <!-- 问卷区 -->
    <el-card class="block">
      <template #header>意向问卷</template>
      <el-empty v-if="!survey" description="问卷未发布" :image-size="60" />
      <template v-else>
        <div class="survey-meta">
          <span>截止时间：{{ survey.deadline || '未设置' }}</span>
          <el-tag size="small" :type="survey.status === 1 ? 'success' : 'info'">{{ survey.status === 1 ? '进行中' : '已截止' }}</el-tag>
        </div>

        <!-- 成员填写 -->
        <el-form v-if="survey.status === 1 && !survey.submitted" label-position="top" class="survey-form">
          <el-form-item v-for="f in survey.fields" :key="f.id" :label="fieldLabel(f)">
            <el-input v-if="f.fieldType === 'text'" v-model="answers[f.id]" maxlength="200" />
            <el-input v-else-if="f.fieldType === 'textarea'" v-model="answers[f.id]" type="textarea" :rows="3" />
            <el-input-number v-else-if="f.fieldType === 'number'" v-model="answers[f.id]" />
            <el-radio-group v-else-if="f.fieldType === 'radio'" v-model="answers[f.id]">
              <el-radio v-for="o in parseOpts(f.options)" :key="o" :value="o">{{ o }}</el-radio>
            </el-radio-group>
            <el-select v-else-if="f.fieldType === 'select'" v-model="answers[f.id]" placeholder="请选择" style="width: 100%">
              <el-option v-for="o in parseOpts(f.options)" :key="o" :label="o" :value="o" />
            </el-select>
            <el-checkbox-group v-else-if="f.fieldType === 'checkbox'" v-model="answers[f.id]">
              <el-checkbox v-for="o in parseOpts(f.options)" :key="o" :value="o">{{ o }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-button type="primary" @click="doSubmitSurvey">提交问卷</el-button>
        </el-form>
        <div v-else-if="survey.submitted" class="hint">✓ 您已提交问卷</div>
        <el-empty v-else description="问卷已截止，不可再提交" :image-size="60" />

        <!-- 结果（管理层） -->
        <template v-if="isManagement && survey.status !== 1 && results">
          <el-divider>问卷结果（{{ results.totalSubmissions }} 人已提交）</el-divider>
          <div v-for="fs in results.fields" :key="fs.fieldId" class="result-item">
            <div class="result-label">{{ fs.label }}</div>
            <div v-if="fs.counts" class="result-counts">
              <el-progress
                v-for="c in fs.counts"
                :key="c.option"
                :percentage="pct(c.count, results.totalSubmissions)"
                :format="() => `${c.option} ${c.count} 人`"
                :stroke-width="14"
              />
            </div>
            <ul v-else class="result-texts">
              <li v-for="(t, i) in fs.texts" :key="i">{{ t }}</li>
            </ul>
          </div>
        </template>
      </template>
    </el-card>

    <!-- 讨论群入口 -->
    <el-card class="block">
      <template #header>讨论群</template>
      <div class="hint" v-if="activity && activity.status < 3">问卷截止后将邀请感兴趣成员与管理员入群讨论</div>
      <template v-else>
        <el-button type="primary" @click="router.push(`/clubs/${clubId}/activities/${activityId}/chat`)">进入讨论群</el-button>
        <div class="hint" v-if="activity && activity.status === 4">活动已发布，讨论群为只读</div>
      </template>
    </el-card>

    <!-- 正式文件 -->
    <el-card class="block">
      <template #header>正式活动文件</template>

      <!-- 发起人草稿编辑（讨论中） -->
      <template v-if="isOwner && activity && activity.status === 3">
        <el-divider content-position="left">
          AI 撰写助手
          <el-button link type="primary" size="small" @click="aiPanelOpen ? (aiPanelOpen = false) : openAiPanel()">
            {{ aiPanelOpen ? '收起' : '打开' }}
          </el-button>
        </el-divider>

        <!-- AI 对话面板（E1：正式文件撰写 Agent，LangGraph 侧实现，产出经人采纳） -->
        <div v-if="aiPanelOpen" class="ai-panel">
          <div class="ai-msgs" ref="aiMsgsRef">
            <template v-for="(m, i) in aiMsgs" :key="i">
              <!-- user -->
              <div v-if="m.role === 'user'" class="ai-msg ai-user">{{ m.content }}</div>
              <!-- assistant -->
              <div v-else-if="m.role === 'assistant'" class="ai-msg ai-assistant">{{ m.content }}</div>
              <!-- tool -->
              <div v-else class="ai-msg ai-tool">
                <div class="ai-tool-name">🔧 {{ m.toolName }}</div>
                <template v-if="m.toolName === 'generate_file_draft'">
                  <template v-if="parseDraft(m)">
                    <div v-for="(s, si) in parseDraft(m).sections" :key="si" class="ai-draft-item">
                      <b>{{ s.title }}</b><span>{{ s.content }}</span>
                    </div>
                    <div v-if="parseDraft(m).decisionNote" class="ai-draft-note">
                      取舍说明：{{ parseDraft(m).decisionNote }}
                    </div>
                    <el-button type="primary" size="small" @click="adoptAiDraft(parseDraft(m).sections)">
                      采纳为草稿（可再编辑）
                    </el-button>
                  </template>
                  <div v-else class="ai-draft-item">{{ m.content }}</div>
                </template>
                <div v-else class="ai-tool-summary">{{ toolSummary(m) }}</div>
              </div>
            </template>
            <div v-if="aiSending" class="ai-msg ai-assistant ai-thinking">AI 正在撰写（约 1-2 分钟）…</div>
          </div>
          <div class="ai-input">
            <el-input v-model="aiInput" placeholder="向 AI 描述正式文件的章节要求，如：根据讨论整理一份正式文件章节"
                      :disabled="aiSending" @keyup.enter="sendAiMsg" />
            <el-button type="primary" :loading="aiSending" :disabled="!aiInput.trim()" @click="sendAiMsg">发送</el-button>
          </div>
        </div>

        <div class="actions" style="margin-bottom: 10px">
          <el-button size="small" @click="addSection">+ 添加章节</el-button>
          <el-button size="small" @click="addDuty">+ 添加分工</el-button>
        </div>
        <div v-for="(s, i) in fileEdit.sections" :key="i" class="file-editor-section">
          <el-input v-model="s.title" placeholder="章节标题（如：活动安排 / 预算 / 安全保障）" style="margin-bottom: 6px" />
          <el-input v-model="s.content" type="textarea" :rows="3" placeholder="章节内容" />
          <el-button link type="danger" @click="fileEdit.sections.splice(i, 1)">删除章节</el-button>
        </div>
        <div v-for="(d, i) in fileEdit.duties" :key="'d' + i" class="file-editor-duty">
          <el-input v-model="d.description" placeholder="职责描述（如：负责路线与骑行安全）" style="flex: 1.2" />
          <el-select v-model="d.memberIds" multiple placeholder="指派成员" style="flex: 1.4">
            <el-option v-for="m in members" :key="m.userId" :label="m.nickname || m.username" :value="m.userId" />
          </el-select>
          <el-button link type="danger" @click="fileEdit.duties.splice(i, 1)">删除</el-button>
        </div>
        <div class="actions" style="margin-top: 12px">
          <el-button @click="doSaveFile">保存草稿</el-button>
          <el-button type="primary" @click="doPublishFile">发布（确定活动）</el-button>
        </div>
      </template>

      <!-- 只读展示（草稿阶段管理层/发布后全员） -->
      <template v-else>
        <el-empty v-if="!file" description="尚未撰写" :image-size="60" />
        <template v-else>
          <div v-for="(s, i) in file.sections" :key="i" class="file-section">
            <h4>{{ s.title }}</h4>
            <p class="file-content">{{ s.content || '（空）' }}</p>
          </div>
          <template v-if="file.duties && file.duties.length">
            <el-divider>分工</el-divider>
            <el-table :data="file.duties" size="small">
              <el-table-column prop="description" label="职责" />
              <el-table-column prop="memberNames" label="负责人" />
            </el-table>
          </template>
        </template>
      </template>
    </el-card>

    <!-- 报名（块 F，状态 5） -->
    <el-card v-if="activity && activity.status === 5" class="block">
      <template #header>活动报名</template>
      <div class="hint" v-if="activity.signupDeadline">截止时间：{{ activity.signupDeadline }}</div>
      <!-- 成员报名 -->
      <el-form label-position="top" class="survey-form">
        <el-form-item label="是否参加本次线下活动">
          <el-radio-group v-model="mySignup.choice">
            <el-radio value="participate">参加</el-radio>
            <el-radio value="not_participate">不参加</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="mySignup.choice === 'not_participate'" label="在线协助（远程支持，将通知发起人）">
          <el-switch v-model="mySignup.onlineAssist" />
        </el-form-item>
        <el-button type="primary" @click="doSignup">提交报名</el-button>
        <div class="hint" v-if="mySignup.saved">✓ 您已报名（截止前可修改）</div>
      </el-form>
      <!-- 管理层名单 -->
      <template v-if="isManagement">
        <el-divider>报名名单（{{ signups.length }} 人）</el-divider>
        <el-table :data="signups" size="small">
          <el-table-column prop="nickname" label="成员" width="140" />
          <el-table-column label="报名" width="100">
            <template #default="{ row }">{{ choiceText(row.choice) }}</template>
          </el-table-column>
          <el-table-column label="在线协助" width="100">
            <template #default="{ row }">{{ row.onlineAssist ? '✓' : '' }}</template>
          </el-table-column>
          <el-table-column label="拦截" width="90">
            <template #default="{ row }"><el-tag v-if="row.blocked" type="danger" size="small">不感兴趣</el-tag></template>
          </el-table-column>
          <el-table-column prop="signupAt" label="报名时间" />
        </el-table>
      </template>
    </el-card>

    <!-- 签到（块 G，状态 6） -->
    <el-card v-if="activity && activity.status === 6" class="block">
      <template #header>活动签到</template>
      <el-button type="primary" :disabled="myCheckedIn" @click="doCheckin">
        {{ myCheckedIn ? '✓ 已签到' : '签到' }}
      </el-button>
      <template v-if="isManagement">
        <el-divider>签到名单（{{ attendances.length }} 人）</el-divider>
        <el-table :data="attendances" size="small">
          <el-table-column prop="nickname" label="成员" width="140" />
          <el-table-column label="签到" width="90">
            <template #default="{ row }">
              <el-tag :type="row.signed ? 'success' : 'info'" size="small">{{ row.signed ? '已签到' : '未签到' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="checkedAt" label="签到时间" />
        </el-table>
      </template>
    </el-card>

    <!-- 执行留痕（块 G，状态 7） -->
    <el-card v-if="activity && activity.status === 7" class="block">
      <template #header>执行留痕</template>
      <div class="hint" v-if="activity.recordDeadline">截止时间：{{ activity.recordDeadline }}</div>
      <!-- 成员提交 -->
      <el-form v-if="recordMine && recordMine.fields" label-position="top" class="survey-form">
        <el-form-item v-for="f in recordMine.fields" :key="f.fieldId" :label="f.label + (f.required === 1 ? '（必填）' : '')">
          <el-input v-if="f.fieldType === 'textarea'" v-model="recordAnswers[f.fieldId]" type="textarea" :rows="3" />
          <el-radio-group v-else-if="f.fieldType === 'radio'" v-model="recordAnswers[f.fieldId]">
            <el-radio v-for="o in f.options" :key="o" :value="o">{{ o }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-button type="primary" @click="doSubmitRecord">提交留痕</el-button>
      </el-form>
      <!-- 管理层：留痕列表 + AI 预评 + 打分 -->
      <template v-if="isManagement">
        <el-divider>留痕列表（{{ records.length }} 条）</el-divider>
        <el-table :data="records" size="small">
          <el-table-column prop="nickname" label="成员" width="130" />
          <el-table-column label="内容" min-width="200">
            <template #default="{ row }">
              <div v-for="a in row.answers" :key="a.fieldId" class="record-answer">
                <b>{{ a.label }}</b>：{{ a.value }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最终分" width="70">
            <template #default="{ row }">{{ row.score ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="AI 预评" width="210">
            <template #default="{ row }">
              <div v-if="row.aiScore != null" class="record-answer">
                <b>{{ row.aiScore }} 分</b>
                <div class="hint">{{ row.aiReason }}</div>
              </div>
              <el-button v-else size="small" :loading="aiScoring === row.userId" @click="doPreviewScore(row)">
                AI 预评
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <template v-if="row.score == null">
                <el-input-number v-model="scoreInputs[row.userId]" :min="0" :max="100" size="small" />
                <el-button size="small" type="primary" @click="doScore(row)">打分</el-button>
              </template>
              <span v-else class="hint">已打分</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>

    <!-- 讨论建议（块 H，讨论关闭后 + 管理层） -->
    <el-card v-if="isManagement && activity && activity.discussionClosedAt && activity.status !== 10" class="block">
      <template #header>
        讨论建议（Java AI 提炼，采纳计质量分）
        <el-button size="small" type="primary" style="float: right" :loading="suggesting" @click="doExtractSuggestions">AI 提炼</el-button>
      </template>
      <el-empty v-if="!suggestions.length" description="尚未提炼建议（需讨论结束后）" :image-size="60" />
      <el-table v-else :data="suggestions" size="small">
        <el-table-column prop="senderNickname" label="建议人" width="120" />
        <el-table-column prop="summary" label="要点" min-width="200" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.adopted ? 'success' : 'info'" size="small">{{ row.adopted ? '已采纳' : '待采纳' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="!row.adopted" size="small" type="primary" plain @click="doAdopt(row)">采纳</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 奖励统计（块 H，管理层） -->
    <el-card v-if="isManagement && activity && activity.status >= 5 && activity.status !== 10" class="block">
      <template #header>
        奖励统计（频率分 + 质量分）
        <el-button size="small" style="float: right" @click="loadRewards">刷新</el-button>
      </template>
      <el-table :data="rewards" size="small">
        <el-table-column prop="nickname" label="成员" width="130" />
        <el-table-column prop="freqScore" label="频率分" width="80" />
        <el-table-column prop="suggestionScore" label="建议分" width="80" />
        <el-table-column prop="recordScore" label="留痕分" width="80" />
        <el-table-column prop="totalScore" label="总分" width="80">
          <template #default="{ row }"><b>{{ row.totalScore }}</b></template>
        </el-table-column>
        <el-table-column label="等级" width="90">
          <template #default="{ row }">
            <el-tag :type="levelType(row.levelName)" size="small">{{ row.levelName }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 活动总结（活动后阶段：总结中 8 / 已归档 9；管理层视图） -->
    <el-card v-if="isManagement && activity && (activity.status === 8 || activity.status === 9)" class="block">
      <template #header>
        活动总结
        <el-tag v-if="summary" size="small" :type="summaryStatusType(summary.status)" style="margin-left: 8px">{{ summaryStatusText(summary.status) }}</el-tag>
        <el-button v-if="summary && summary.status !== 'pending'" size="small" style="float: right" :loading="summarizing" @click="doRegenerate">重新生成</el-button>
      </template>

      <!-- 未生成 / 生成中 -->
      <el-skeleton v-if="!summary || summary.status === 'pending'" :rows="4" animated />
      <div v-if="!summary" class="hint">总结尚未生成（进入总结中后自动触发，失败自动重试），可稍候刷新</div>
      <div v-else-if="summary.status === 'pending'" class="hint">总结生成中，请稍候刷新（约 1 分钟）</div>

      <!-- 生成失败 -->
      <el-result v-else-if="summary.status === 'failed'" icon="error" title="总结生成失败"
        sub-title="可点击右上角重新生成，系统也会每分钟自动重试（最多 3 次）" />

      <!-- 待确认问题（跨语言中断恢复） -->
      <template v-else-if="summary.status === 'awaiting'">
        <el-alert type="warning" show-icon :closable="false" title="AI 需要补充信息才能完成总结" class="block" />
        <div v-for="q in summary.questions" :key="q.id" class="result-item">
          <div class="result-label">{{ q.question }}</div>
          <el-input v-model="summaryAnswers[q.id]" placeholder="请输入说明（将纳入总结）" />
        </div>
        <el-button type="primary" :loading="summarizing" @click="doResume">提交回答，继续生成</el-button>
      </template>

      <!-- 已生成：结构化指标 + AI 报告 -->
      <template v-else>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="报名">{{ m(summary.metrics?.signup, 'total') }} 人（参加 {{ m(summary.metrics?.signup, 'participate') }} / 在线协助 {{ m(summary.metrics?.signup, 'online_assist') }} / 不感兴趣 {{ m(summary.metrics?.signup, 'not_interested') }}）</el-descriptions-item>
          <el-descriptions-item label="签到">应到 {{ m(summary.metrics?.attendance, 'expected') }} / 实到 {{ m(summary.metrics?.attendance, 'present') }}</el-descriptions-item>
          <el-descriptions-item label="留痕">提交 {{ m(summary.metrics?.record, 'submitted') }} 份 / 覆盖率 {{ m(summary.metrics?.record, 'coverage') }} / 均分 {{ m(summary.metrics?.record, 'avg_score') }}</el-descriptions-item>
          <el-descriptions-item label="讨论">消息 {{ m(summary.metrics?.discussion, 'message_count') }} 条 / 质量率 {{ m(summary.metrics?.discussion, 'quality_rate') }}</el-descriptions-item>
          <el-descriptions-item label="奖励">采纳建议 {{ m(summary.metrics?.reward, 'adopted_suggestions') }} 条 / 最高分 {{ m(summary.metrics?.reward, 'top_score') }}</el-descriptions-item>
          <el-descriptions-item label="经验库">沉淀 {{ (summary.lessons || []).length }} 条</el-descriptions-item>
        </el-descriptions>
        <el-divider>AI 总结报告</el-divider>
        <div class="report-text">{{ summary.reportText }}</div>
        <div v-if="activity.status === 8" class="hint" style="margin-top: 8px">确认无误后可点击上方「归档活动」，归档后全员可查看本报告</div>
      </template>
    </el-card>

    <!-- 时间线 -->
    <el-card class="block">
      <template #header>时间线</template>
      <el-timeline>
        <el-timeline-item
          v-for="t in (activity?.traces || [])"
          :key="t.id"
          :timestamp="t.createdAt"
          placement="top"
        >
          <b>{{ traceText(t.action) }}</b>
          <div class="hint">{{ t.detail || '' }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- 活动资料（双项目集成：入社团知识库，概念 Agent 起草新活动时自动检索复用） -->
    <el-card v-if="activity" class="block">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>活动资料</span>
          <el-upload
            v-if="isManagement"
            multiple
            :show-file-list="false"
            :http-request="queueUploadLib"
            accept=".txt,.md,.pdf,.docx,.xlsx,.pptx,.png,.jpg,.jpeg,.webp"
          >
            <el-button size="small" type="primary" :loading="uploadingLib">上传资料</el-button>
          </el-upload>
        </div>
      </template>
      <div class="hint" style="margin-bottom: 8px">资料将解析入社团知识库，新活动起草时 Agent 可自动检索复用（管理层上传，全员可读）</div>
      <el-table :data="fileLib" size="small" v-loading="loadingLib">
        <el-table-column label="文件" min-width="220">
          <template #default="{ row }">
            <a :href="row.storageUrl" target="_blank">{{ row.filename }}</a>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="uploaderName" label="上传人" width="110" />
        <el-table-column label="入库状态" width="110">
          <template #default="{ row }">
            <el-tag :type="ragTagType(row.ragStatus)" size="small">{{ ragStatusText(row.ragStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="170" />
        <el-table-column v-if="isManagement" label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" type="danger" link @click="handleDeleteLib(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 发布问卷弹窗 -->
    <el-dialog v-model="surveyDialog" title="发布意向问卷" width="640px">
      <el-form label-width="90px">
        <el-form-item label="截止时间" required>
          <el-date-picker v-model="surveyForm.deadline" type="datetime" placeholder="选择截止时间" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="自定义题">
          <div class="field-editor">
            <div v-for="(f, i) in surveyForm.fields" :key="i" class="field-row">
              <el-input v-model="f.label" placeholder="题目内容" style="flex: 1.4" />
              <el-select v-model="f.fieldType" style="width: 110px">
                <el-option label="单选" value="radio" />
                <el-option label="多选" value="checkbox" />
                <el-option label="下拉" value="select" />
                <el-option label="短文本" value="text" />
                <el-option label="长文本" value="textarea" />
              </el-select>
              <el-switch v-model="f.required" active-text="必答" />
              <el-button link type="danger" @click="surveyForm.fields.splice(i, 1)">删除</el-button>
              <el-input
                v-if="['radio', 'select', 'checkbox'].includes(f.fieldType)"
                v-model="f.optionsText"
                placeholder="选项用英文逗号分隔，如：感兴趣,不感兴趣"
                style="width: 100%; margin-top: 6px"
              />
            </div>
            <el-button link type="primary" @click="addField">+ 添加题目</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="surveyDialog = false">取消</el-button>
        <el-button type="primary" @click="doPublishSurvey">发布</el-button>
      </template>
    </el-dialog>

    <!-- 开始报名弹窗（状态 4） -->
    <el-dialog v-model="signupDialog" title="开始报名" width="420px">
      <el-form label-width="90px">
        <el-form-item label="报名截止" required>
          <el-date-picker v-model="signupForm.deadline" type="datetime" placeholder="选择报名截止时间" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="signupDialog = false">取消</el-button>
        <el-button type="primary" @click="doStartSignup">确定开放报名</el-button>
      </template>
    </el-dialog>

    <!-- 开始执行弹窗（状态 5，可选留痕截止） -->
    <el-dialog v-model="execDialog" title="开始执行" width="420px">
      <el-form label-width="120px">
        <el-form-item label="留痕截止时间">
          <el-date-picker v-model="execForm.deadline" type="datetime" placeholder="不设则留痕开放至手动关闭" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="execDialog = false">取消</el-button>
        <el-button type="primary" @click="doStartExecution">确定开始执行</el-button>
      </template>
    </el-dialog>

    <!-- 取消弹窗 -->
    <el-dialog v-model="cancelDialog" title="取消活动" width="420px">
      <el-input v-model="cancelReason" type="textarea" :rows="3" placeholder="必填：取消理由（将通知全体成员）" />
      <template #footer>
        <el-button @click="cancelDialog = false">返回</el-button>
        <el-button type="danger" @click="doCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getActivityDetail, cancelActivity,
  publishSurvey, getSurvey, submitSurvey, getSurveyResults, closeSurvey,
  getActivityFile, saveActivityFile, publishActivityFile,
  aiChatActivity, aiSessionActivity,
  endDiscussion, startSignup, startExecution, completeExecution, closeRecords,
  signupActivity, getSignups,
  checkinActivity, getAttendances,
  getRecordMine, submitRecord, getRecords,
  previewRecordScore, scoreRecord, getRecordScores,
  extractSuggestions, adoptSuggestion, getSuggestions,
  getRewards,
  getActivitySummary, regenerateSummary, resumeSummary, archiveActivity,
  getFileLib, uploadFileLib, deleteFileLib
} from '../api/activity'
import { getClubDetail, getMembers } from '../api/club'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const clubId = route.params.clubId
const activityId = route.params.id
const userStore = useUserStore()
const myUserId = String(userStore.userInfo?.userId ?? userStore.userInfo?.id ?? '')

const loading = ref(false)
const activity = ref(null)
const myRoleCode = ref('')
const isManagement = computed(() => ['president', 'vice_president'].includes(myRoleCode.value))
const isOwner = computed(() => activity.value && String(activity.value.userId) === myUserId)

const survey = ref(null)
const answers = ref({})
const results = ref(null)
const surveyDialog = ref(false)
const surveyForm = ref({ deadline: null, fields: [] })
const cancelDialog = ref(false)
const cancelReason = ref('')
const file = ref(null)
const fileEdit = ref({ sections: [], duties: [] })
const members = ref([])
// ---- E1 AI 撰写面板 ----
const aiPanelOpen = ref(false)
const aiMsgs = ref([])
const aiInput = ref('')
const aiSending = ref(false)
const aiMsgsRef = ref(null)
// ---- 块 F/G/H：报名 / 签到 / 留痕 / 建议 / 奖励 ----
const signupDialog = ref(false)
const signupForm = ref({ deadline: null })
const execDialog = ref(false)
const execForm = ref({ deadline: null })
const mySignup = ref({ choice: 'participate', onlineAssist: false, saved: false })
const signups = ref([])
const attendances = ref([])
const myCheckedIn = ref(false)
const recordMine = ref(null)
const recordAnswers = ref({})
const records = ref([])
const scoreInputs = ref({})
const aiScoring = ref('')
const suggestions = ref([])
const panelError = ref('')
const suggesting = ref(false)
const rewards = ref([])
// ---- 活动后阶段：总结 + 归档 ----
const summary = ref(null)
const summaryAnswers = ref({})
const summarizing = ref(false)
const archiving = ref(false)

const statusText = (s) => ({ 1: '公示中', 2: '问卷中', 3: '讨论中', 4: '已发布', 5: '报名中', 6: '执行中', 7: '留痕中', 8: '总结中', 9: '已归档', 10: '已取消' }[s] ?? '未知')
const statusType = (s) => ({ 1: 'primary', 2: 'warning', 3: 'warning', 4: 'success', 5: 'primary', 6: 'warning', 7: 'warning', 8: 'info', 9: 'info', 10: 'info' }[s] ?? 'info')
const traceText = (a) => ({ create: '活动成立', survey_publish: '发布问卷', discuss_start: '开启讨论', end_discussion: '结束讨论', file_publish: '发布正式文件', start_signup: '开始报名', start_execution: '开始执行', complete_execution: '结束执行', record_close: '关闭留痕', archive: '归档活动', cancel: '取消活动' }[a] ?? a)

// ---- 活动后阶段：总结状态映射 ----
const summaryStatusText = (s) => ({ pending: '生成中', awaiting: '待补充信息', success: '已生成', failed: '生成失败' }[s] ?? s)
const summaryStatusType = (s) => ({ pending: 'info', awaiting: 'warning', success: 'success', failed: 'danger' }[s] ?? 'info')
const m = (obj, key, fallback = '-') => (obj == null ? fallback : (obj[key] ?? fallback))

const parseOpts = (json) => {
  if (!json) return []
  try { return JSON.parse(json) } catch (e) { return [] }
}
const fieldLabel = (f) => (f.systemFlag === 1 ? `${f.label}（必答）` : `${f.label}${f.required === 1 ? '（必答）' : ''}`)

async function load() {
  loading.value = true
  try {
    const [a, club] = await Promise.all([
      getActivityDetail(clubId, activityId),
      getClubDetail(clubId)
    ])
    activity.value = a.data
    myRoleCode.value = club.data?.myRoleCode || ''
    await loadSurvey()
    await loadFile()
    await loadStagePanels()
  } finally {
    loading.value = false
  }
}

// ---- 块 F/G/H：按状态加载面板数据 ----
async function loadStagePanels() {
  const s = activity.value?.status
  if (s === 5) {
    await Promise.all([loadMySignup(), isManagement.value ? loadSignups() : Promise.resolve()])
  } else if (s === 6) {
    await Promise.all([loadMyCheckedIn(), isManagement.value ? loadAttendances() : Promise.resolve()])
  } else if (s === 7) {
    await Promise.all([loadRecordMine(), isManagement.value ? loadRecords() : Promise.resolve()])
  }
  if (isManagement.value && activity.value?.discussionClosedAt && activity.value?.status !== 10) {
    await Promise.all([loadSuggestions(), loadRewards()])
  }
  if (isManagement.value && (s === 8 || s === 9)) {
    await loadSummary()
  }
}

// ---- 活动后阶段：总结 + 归档 ----
async function loadSummary() {
  try {
    const r = await getActivitySummary(clubId, activityId)
    summary.value = r.data || null
  } catch (e) {
    if (e?.code === 1053) { summary.value = null; return }  // 尚未生成：展示引导文案
    panelFail(e, '活动总结')
  }
}

async function doRegenerate() {
  summarizing.value = true
  try {
    await regenerateSummary(clubId, activityId)
    summary.value = { status: 'pending' }
    ElMessage.success('已开始重新生成，稍候刷新查看')
  } catch (e) {
    ElMessage.error(e?.msg || '重新生成失败')
  } finally {
    summarizing.value = false
  }
}

async function doResume() {
  summarizing.value = true
  try {
    await resumeSummary(clubId, activityId, summaryAnswers.value)
    await loadSummary()
    ElMessage.success('回答已提交，总结继续生成')
  } catch (e) {
    ElMessage.error(e?.msg || '提交失败')
  } finally {
    summarizing.value = false
  }
}

async function doArchive() {
  try {
    await ElMessageBox.confirm('归档后活动进入已归档（只读），全员可查看总结报告；仍可重新生成总结。确认归档？', '归档活动', { type: 'warning' })
  } catch { return }
  archiving.value = true
  try {
    await archiveActivity(clubId, activityId)
    ElMessage.success('已归档')
    await load()
  } catch (e) {
    ElMessage.error(e?.msg || '归档失败')
  } finally {
    archiving.value = false
  }
}

// 面板加载失败统一处理：console 留痕 + 页面提示（不静默，避免“空列表分不清是没数据还是加载失败”）
function panelFail(e, name) {
  console.error(`[${name} 加载失败]`, e)
  panelError.value = `部分数据加载失败：${name}，请刷新重试`
}

// ---- 报名 ----
async function loadMySignup() {
  try {
    const res = await getSignups(clubId, activityId)
    const me = (res.data || []).find((x) => String(x.userId) === myUserId)
    mySignup.value = me
      ? { choice: me.choice, onlineAssist: !!me.onlineAssist, saved: true }
      : { choice: 'participate', onlineAssist: false, saved: false }
    panelError.value = ''
  } catch (e) { panelFail(e, '我的报名') }
}
async function doSignup() {
  await signupActivity(clubId, activityId, {
    choice: mySignup.value.choice,
    onlineAssist: mySignup.value.onlineAssist
  })
  ElMessage.success('报名成功')
  mySignup.value.saved = true
  if (isManagement.value) await loadSignups()
}
async function loadSignups() {
  try {
    const res = await getSignups(clubId, activityId)
    signups.value = res.data || []
    panelError.value = ''
  } catch (e) { panelFail(e, '报名名单') }
}
const choiceText = (c) => ({ participate: '参加', not_participate: '不参加' }[c] ?? '未报名')

// ---- 签到 ----
async function loadMyCheckedIn() {
  try {
    const res = await getAttendances(clubId, activityId)
    myCheckedIn.value = (res.data || []).some((x) => String(x.userId) === myUserId && x.signed)
    panelError.value = ''
  } catch (e) { myCheckedIn.value = false; panelFail(e, '我的签到') }
}
async function doCheckin() {
  await checkinActivity(clubId, activityId)
  ElMessage.success('签到成功')
  myCheckedIn.value = true
  if (isManagement.value) await loadAttendances()
}
async function loadAttendances() {
  try {
    const res = await getAttendances(clubId, activityId)
    attendances.value = res.data || []
    panelError.value = ''
  } catch (e) { panelFail(e, '签到名单') }
}

// ---- 留痕 ----
async function loadRecordMine() {
  try {
    const res = await getRecordMine(clubId, activityId)
    recordMine.value = res.data
    recordAnswers.value = {}
    for (const a of res.data?.answers || []) recordAnswers.value[a.fieldId] = a.value
    panelError.value = ''
  } catch (e) { recordMine.value = null; panelFail(e, '我的留痕') }
}
async function doSubmitRecord() {
  // fieldId 后端按字符串序列化（雪花 ID 超 JS 安全整数），直接透传，禁止 Number() 转换
  const answers = Object.entries(recordAnswers.value).map(([fieldId, value]) => ({ fieldId, value: value ?? '' }))
  await submitRecord(clubId, activityId, { answers })
  ElMessage.success('留痕已提交')
  await loadRecordMine()
}
async function loadRecords() {
  try {
    const res = await getRecords(clubId, activityId)
    records.value = res.data || []
    panelError.value = ''
  } catch (e) { panelFail(e, '留痕列表') }
}

// ---- 留痕打分（Java AI 预评 + 管理员确认） ----
async function doPreviewScore(row) {
  aiScoring.value = row.userId
  try {
    const res = await previewRecordScore(clubId, activityId, row.userId)
    row.aiScore = res.data?.aiScore
    row.aiReason = res.data?.aiReason
  } catch (e) {
    // 拦截器已 toast，这里只需避免 unhandled rejection
    console.error('[AI 预评失败]', e)
  } finally {
    aiScoring.value = ''
  }
}
async function doScore(row) {
  const score = scoreInputs.value[row.userId]
  if (score == null) return ElMessage.warning('请输入分数')
  await scoreRecord(clubId, activityId, row.userId, Number(score))
  ElMessage.success('打分完成')
  await loadRecords()
}

// ---- 讨论建议（Java AI 提炼） ----
async function loadSuggestions() {
  try {
    const res = await getSuggestions(clubId, activityId)
    suggestions.value = res.data || []
    panelError.value = ''
  } catch (e) { suggestions.value = []; panelFail(e, '讨论建议') }
}
async function doExtractSuggestions() {
  suggesting.value = true
  try {
    const res = await extractSuggestions(clubId, activityId)
    suggestions.value = res.data || []
    ElMessage.success('AI 提炼完成')
  } finally {
    suggesting.value = false
  }
}
async function doAdopt(row) {
  await adoptSuggestion(clubId, activityId, row.id)
  ElMessage.success('已采纳')
  row.adopted = true
  await loadRewards()
}

// ---- 奖励统计 ----
async function loadRewards() {
  try {
    const res = await getRewards(clubId, activityId)
    rewards.value = res.data || []
    panelError.value = ''
  } catch (e) { rewards.value = []; panelFail(e, '奖励统计') }
}
const levelType = (l) => ({ 优秀: 'success', 良好: 'primary', 合格: 'warning', 待提升: 'info' }[l] ?? 'info')

// ---- 状态机推进 ----
async function doEndDiscussion() {
  await ElMessageBox.confirm('讨论将结束，群转只读，生成讨论质量快照并解锁正式文件撰写，确认？', '结束讨论', { type: 'warning' })
  await endDiscussion(clubId, activityId)
  ElMessage.success('讨论已结束')
  await load()
}
async function doStartSignup() {
  if (!signupForm.value.deadline) return ElMessage.warning('请选择报名截止时间')
  await startSignup(clubId, activityId, signupForm.value.deadline)
  ElMessage.success('报名已开放')
  signupDialog.value = false
  await load()
}
async function doStartExecution() {
  await startExecution(clubId, activityId, execForm.value.deadline || null)
  ElMessage.success('活动开始执行')
  execDialog.value = false
  await load()
}
async function doCompleteExecution() {
  await ElMessageBox.confirm('执行完成，开放执行留痕提交，确认？', '结束执行', { type: 'warning' })
  await completeExecution(clubId, activityId)
  ElMessage.success('已开放留痕')
  await load()
}
async function doCloseRecords() {
  await ElMessageBox.confirm('关闭留痕后进入总结阶段，系统将自动生成活动总结，确认？', '关闭留痕', { type: 'warning' })
  await closeRecords(clubId, activityId)
  ElMessage.success('留痕已关闭')
  await load()
}

async function loadSurvey() {
  try {
    const res = await getSurvey(clubId, activityId)
    survey.value = res.data
  } catch (e) {
    survey.value = null // 未发布
  }
}

async function loadResults() {
  try {
    const res = await getSurveyResults(clubId, activityId)
    results.value = res.data
    panelError.value = ''
  } catch (e) { panelFail(e, '问卷结果') }
}

async function loadFile() {
  try {
    const res = await getActivityFile(clubId, activityId)
    file.value = res.data
    // 编辑态初始化（发起人草稿）
    fileEdit.value = {
      sections: (res.data.sections || []).map((s) => ({ ...s })),
      duties: (res.data.duties || []).map((d) => ({
        description: d.description,
        memberIds: JSON.parse(d.memberIds || '[]')
      }))
    }
  } catch (e) {
    file.value = null
    fileEdit.value = { sections: [], duties: [] }
  }
}

const pct = (n, total) => (total ? Math.round((n / total) * 100) : 0)

// ---- 问卷 ----
function addField() {
  surveyForm.value.fields.push({ label: '', fieldType: 'radio', required: true, optionsText: '' })
}
async function doPublishSurvey() {
  const deadline = surveyForm.value.deadline
  if (!deadline) return ElMessage.warning('请选择截止时间')
  const fields = surveyForm.value.fields
    .filter((f) => f.label.trim())
    .map((f) => ({
      label: f.label.trim(),
      fieldType: f.fieldType,
      required: f.required ? 1 : 0,
      options: ['radio', 'select', 'checkbox'].includes(f.fieldType)
        ? f.optionsText.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
        : undefined
    }))
  if (fields.some((f) => ['radio', 'select', 'checkbox'].includes(f.fieldType) && !(f.options || []).length)) {
    return ElMessage.warning('选项题必须填写选项')
  }
  await publishSurvey(clubId, activityId, { deadline, fields })
  ElMessage.success('问卷已发布')
  surveyDialog.value = false
  await Promise.all([load(), loadSurvey()])
}

async function doSubmitSurvey() {
  const list = []
  for (const f of survey.value.fields || []) {
    let v = answers.value[f.id]
    if (Array.isArray(v)) v = JSON.stringify(v)
    list.push({ fieldId: f.id, value: v == null ? '' : String(v) })
  }
  await submitSurvey(clubId, activityId, { answers: list })
  ElMessage.success('提交成功')
  survey.value.submitted = true
}

async function doCloseSurvey() {
  await ElMessageBox.confirm('问卷将截止，感兴趣成员与管理员统一进入讨论群，确认开启讨论？', '开启讨论', { type: 'warning' })
  await closeSurvey(clubId, activityId)
  ElMessage.success('已开启讨论')
  await load()
}

// ---- 正式文件 ----
function scrollToFile() {
  document.querySelector('.block')?.scrollIntoView({ behavior: 'smooth' })
}
function addSection() {
  fileEdit.value.sections.push({ title: '', content: '' })
}
function addDuty() {
  fileEdit.value.duties.push({ description: '', memberIds: [] })
}
async function doSaveFile() {
  const sections = fileEdit.value.sections.filter((s) => s.title.trim())
  if (!sections.length) return ElMessage.warning('至少填写一个章节')
  await saveActivityFile(clubId, activityId, { sections })
  ElMessage.success('草稿已保存')
  await loadFile()
}
async function doPublishFile() {
  const sections = fileEdit.value.sections.filter((s) => s.title.trim())
  const duties = fileEdit.value.duties
    .filter((d) => d.description.trim() && d.memberIds.length)
    .map((d) => ({ description: d.description.trim(), memberIds: d.memberIds }))
  if (!sections.length) return ElMessage.warning('至少填写一个章节')
  if (!duties.length) return ElMessage.warning('至少填写一项分工（含负责人）')
  await ElMessageBox.confirm('发布后活动确定、讨论群只读，全员将收到正式文件，确认发布？', '发布正式文件', { type: 'warning' })
  await publishActivityFile(clubId, activityId, { sections, duties })
  ElMessage.success('正式文件已发布，活动确定')
  await Promise.all([load(), loadFile()])
}

// ---- E1 AI 撰写助手 ----
function parseDraft(m) {
  // generate_file_draft 工具结果：JSON { sections: [{title, content}], decision_note }
  if (!m?.content) return null
  try {
    const o = JSON.parse(m.content)
    if (Array.isArray(o.sections) && o.sections.length) {
      return { sections: o.sections, decisionNote: o.decision_note || '' }
    }
  } catch (e) { /* 非 JSON（如生成失败提示），回退展示原文 */ }
  return null
}
function toolSummary(m) {
  // 其他工具（get_activity_context 等）：展示结果摘要
  const c = (m.content || '').trim()
  return c.length > 120 ? c.slice(0, 120) + '…' : c
}
function scrollAiMsgs() {
  setTimeout(() => { aiMsgsRef.value?.scrollTo?.({ top: 99999, behavior: 'smooth' }) }, 30)
}
async function openAiPanel() {
  aiPanelOpen.value = true
  scrollAiMsgs()
  if (aiMsgs.value.length) return
  try {
    const res = await aiSessionActivity(clubId, activityId)
    aiMsgs.value = res.data || []
    scrollAiMsgs()
  } catch (e) { /* 无会话：空面板 */ }
}
async function sendAiMsg() {
  const msg = aiInput.value.trim()
  if (!msg || aiSending.value) return
  aiSending.value = true
  aiInput.value = ''
  aiMsgs.value.push({ role: 'user', content: msg })
  scrollAiMsgs()
  try {
    const res = await aiChatActivity(clubId, activityId, msg)
    aiMsgs.value = res.data || []
    ElMessage.success('AI 已回复')
  } catch (e) {
    // 业务错误已由拦截器弹窗；仅网络异常（无 response）补兑底提示
    if (!e?.response) ElMessage.error('AI 暂不可用，请稍后重试或手动撰写')
  } finally {
    aiSending.value = false
    scrollAiMsgs()
  }
}
function adoptAiDraft(sections) {
  // 人采纳 AI 章节草稿 → 落入编辑器（保存/发布仍由人操作，AI 无写权限）
  fileEdit.value.sections = (sections || []).map((s) => ({ title: s.title || '', content: s.content || '' }))
  aiPanelOpen.value = false
  ElMessage.success('已采纳 AI 章节草稿，请检查后保存/发布')
  scrollToFile()
}

// 成员列表（分工指派用）
async function loadMembers() {
  try {
    const res = await getMembers(clubId)
    members.value = res.data || []
    panelError.value = ''
  } catch (e) {
    members.value = []
    panelFail(e, '成员列表')
  }
}

// ---- 取消 ----
async function doCancel() {
  if (!cancelReason.value.trim()) return ElMessage.warning('取消理由必填')
  await cancelActivity(clubId, activityId, cancelReason.value.trim())
  ElMessage.success('活动已取消')
  cancelDialog.value = false
  await load()
}

onMounted(() => {
  load()
  loadMembers()
  loadFileLib()
})

// ===== 活动资料（双项目集成：上传 → 入 rag 知识库 → 概念 Agent 检索复用） =====
const fileLib = ref([])
const uploadingLib = ref(false)
const loadingLib = ref(false)

const loadFileLib = async () => {
  loadingLib.value = true
  try {
    const res = await getFileLib(clubId)
    fileLib.value = res.data || []
  } catch {
    fileLib.value = []
  } finally {
    loadingLib.value = false
  }
}

// 批量上传（J4）：multiple 选中后逐个串行上传，避免并发刷新列表竞态；全部完成后统一刷新
let uploadQueue = Promise.resolve()
let uploadPending = 0
const queueUploadLib = ({ file }) => {
  uploadPending++
  uploadingLib.value = true
  uploadQueue = uploadQueue
    .then(() => handleUploadLib(file))
    .finally(() => {
      uploadPending--
      if (uploadPending === 0) {
        uploadingLib.value = false
        loadFileLib()
      }
    })
  return uploadQueue
}

const handleUploadLib = async (file) => {
  try {
    const fd = new FormData()
    fd.append('file', file)
    await uploadFileLib(clubId, fd, activityId)
    ElMessage.success(`「${file.name}」上传成功，正在解析入库（入库后可被 Agent 检索）`)
    await loadFileLib()
  } catch {
    // 错误提示由响应拦截器统一弹出（重名/超限/服务未启用等）；批量中单个失败不阻断后续
  }
}

const handleDeleteLib = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除资料「${row.filename}」？删除后将不可被检索。`, '确认删除', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteFileLib(clubId, row.id)
    ElMessage.success('已删除')
    await loadFileLib()
  } catch {
    // 拦截器已弹错
  }
}

const ragStatusText = (s) => ({ pending: '待入库', parsing: '解析中', success: '可检索', partial: '可检索(部分)', failed: '入库失败', voided: '已失效' }[s] || s)
const ragTagType = (s) => ({ success: 'success', partial: 'warning', parsing: 'info', failed: 'danger', voided: 'info' }[s] || '')
const formatSize = (n) => {
  if (!n) return '-'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}
</script>

<style scoped>
.page { max-width: 900px; margin: 0 auto; padding: 16px; }
.header { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.header h2 { margin: 0; flex: 1; }
.block { margin-bottom: 14px; }
.actions { display: flex; gap: 10px; flex-wrap: wrap; }
.survey-meta { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; color: #666; font-size: 13px; }
.survey-form { max-width: 560px; }
.field-editor { width: 100%; }
.field-row { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; flex-wrap: wrap; }
.result-item { margin-bottom: 14px; }
.result-label { font-weight: 600; margin-bottom: 6px; }
.result-counts .el-progress { margin-bottom: 6px; }
.result-texts { margin: 0; padding-left: 18px; color: #555; }
.file-section h4 { margin: 10px 0 4px; }
.file-content { white-space: pre-wrap; color: #444; margin: 0 0 8px; }
.hint { color: #999; font-size: 13px; margin-top: 6px; }
.report-text { white-space: pre-wrap; line-height: 1.7; color: #444; background: #fafafa; border: 1px solid #e4e7ed; border-radius: 6px; padding: 12px; font-size: 14px; }
.danger { color: #f56c6c; }
.ai-panel { border: 1px solid #e4e7ed; border-radius: 6px; padding: 10px; margin-bottom: 12px; background: #fafafa; }
.ai-msgs { max-height: 320px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; margin-bottom: 10px; }
.ai-msg { max-width: 92%; padding: 8px 12px; border-radius: 8px; font-size: 13px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.ai-user { align-self: flex-end; background: #409eff; color: #fff; }
.ai-assistant { align-self: flex-start; background: #fff; border: 1px solid #e4e7ed; }
.ai-tool { align-self: flex-start; width: 92%; background: #f4f4f5; border: 1px dashed #dcdfe6; }
.ai-tool-name { font-size: 12px; color: #909399; margin-bottom: 4px; }
.ai-tool-summary { font-size: 12px; color: #606266; white-space: pre-wrap; }
.ai-draft-item { margin-bottom: 6px; }
.ai-draft-item b { display: block; margin-bottom: 2px; }
.ai-draft-item span { color: #555; white-space: pre-wrap; }
.ai-draft-note { color: #e6a23c; font-size: 12px; margin: 6px 0; }
.ai-input { display: flex; gap: 8px; }
.ai-thinking { color: #909399; font-style: italic; }
.record-answer { font-size: 13px; color: #444; margin-bottom: 2px; }
.record-answer b { color: #606266; }
</style>
