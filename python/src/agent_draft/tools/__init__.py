"""工具注册表：按会话类型分集（概念起草 / 正式文件撰写）。

- CONCEPT_TOOLS：概念阶段（D2-D4 累计 7 个）
- FILE_TOOLS：活动正式文件撰写（活动前 Agent：活动上下文 + 章节起草 + 经验检索 + 社团背景）
全部只读/生成，不落库——写操作只能由人经前端触发。
"""
from .activity_context import get_activity_context
from .context import get_club_context
from .draft import generate_draft, get_draft
from .experience import search_experience
from .extract import extract_experience, extract_thinking_pattern
from .file_draft import generate_file_draft
from .skill import generate_skill

CONCEPT_TOOLS = [
    search_experience,        # T1
    get_club_context,         # T3
    get_draft,                # T4
    generate_draft,           # T5
    extract_experience,       # T7a（D3：草拟经验，人确认后落库）
    extract_thinking_pattern, # T7b（D3：草拟思考角度，人确认后落库）
    generate_skill,           # T8（D4：草拟 SKILL.md，人确认后落盘）
]

FILE_TOOLS = [
    get_activity_context,     # T9：活动前置上下文（概念/讨论/问卷三源）
    generate_file_draft,      # T10：章节草稿生成（人确认后由前端写入）
    search_experience,        # T1 复用：历史经验参考
    get_club_context,         # T3 复用：社团背景
]
