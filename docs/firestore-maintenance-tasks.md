# Modelo Firestore: tareas de mantenimiento

## Colección

- `tenants/{tenantId}/maintenance_tasks/{taskId}`

## Campos recomendados

```json
{
  "tenantId": "tenant_abc",
  "title": "Revisar impresora fiscal",
  "description": "No imprime tickets en caja 2",
  "status": "pending",
  "priority": "high",
  "operationalBlocker": true,
  "assigneeUid": "uid_operador",
  "dueAt": "Timestamp",
  "createdAt": "Timestamp",
  "updatedAt": "Timestamp",
  "createdByUid": "uid_owner",
  "updatedByUid": "uid_owner",
  "trace": {
    "lastAction": "create|update",
    "source": "cloud_function"
  }
}
```

## Estados

- `pending`
- `in_progress`
- `blocked`
- `completed`
- `cancelled`

## Prioridad

- `low`
- `medium`
- `high`
- `critical`

## Índices sugeridos

- `(status ASC, dueAt ASC)`
- `(operationalBlocker DESC, priority DESC, dueAt ASC)`
- `(assigneeUid ASC, status ASC, updatedAt DESC)`
