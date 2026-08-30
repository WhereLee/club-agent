"""PostgresSaver 检查点：会话续聊恢复（thread_id = concept_id）。

业务事实源是 Java 侧 concept_draft_session 表；checkpoint 只是运行态缓存，
服务重启后可从检查点恢复对话上下文。
"""
import logging

import psycopg
from langgraph.checkpoint.postgres import PostgresSaver

from . import config

logger = logging.getLogger(__name__)

_saver: PostgresSaver | None = None


def get_saver() -> PostgresSaver:
    """懒加载单例。

    注意：langgraph-checkpoint-postgres 的 from_conn_string 是上下文管理器（with 块内使用），
    不适合常驻服务；这里手动持有进程级 psycopg 连接（autocommit），
    PostgresSaver 内部有锁保护连接，多线程安全。
    """
    global _saver
    if _saver is None:
        conn = psycopg.connect(
            config.DB_URL,
            autocommit=True,
            prepare_threshold=0,
            row_factory=psycopg.rows.dict_row,
        )
        _saver = PostgresSaver(conn)
        _saver.setup()  # 幂等：建 checkpoint 相关表
        logger.info("PostgresSaver 就绪：%s", config.DB_URL.split("@")[-1])
    return _saver
