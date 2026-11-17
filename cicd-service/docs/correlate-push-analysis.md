# Relacionar el push con el análisis

La migración V8 agrega la vista `cicd.push_analysis_receipts`. Consulta los recibos
ya almacenados: no requiere otro push ni reenviar el resultado anterior.

Relaciona el ID de repositorio, la referencia completa de rama, la revisión previa
y la revisión analizada. Conserva cada intento de Actions por separado. Funciona
con cualquier orden de llegada y excluye los pushes que eliminan ramas.

Desde una sesión autorizada en PostgreSQL:

```sql
SELECT delivery_id, repository_id, owner_login, repository_name, git_ref,
       after_sha, project_key, task_id, workflow_run_id, workflow_run_attempt,
       analysis_status, quality_gate_status
FROM cicd.push_analysis_receipts
WHERE owner_login = 'PRGATE-TEST-SQU' AND repository_name = 'backend'
ORDER BY push_received_at DESC, sonar_received_at DESC;
```

`analysis_status = SUCCESS` significa que la tarea terminó; el resultado del
Quality Gate se consulta en otra columna. Un resultado nulo significa que no hay
un recibo coincidente: no permite afirmar que falló o que fue omitido por reglas.

Esta es una asociación técnica de recibos autenticados por el webhook de Sonar.
Todavía no verifica por la API de GitHub que los identificadores de ejecución
declarados por el scanner pertenezcan al repositorio. No sustituye la autorización
de Projects ni se publica como endpoint sin permisos para repositorios privados.
La integración con el registro de pipelines y las consultas autorizadas sigue
siendo un paso posterior.
