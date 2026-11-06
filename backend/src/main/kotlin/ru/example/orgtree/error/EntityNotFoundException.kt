package ru.example.orgtree.error

/** Отличает ожидаемое отсутствие сущности от непредвиденной ошибки сервера. */
class EntityNotFoundException(val entity: String, id: Long?) :
    RuntimeException(if (id == null) "Корень дерева не найден" else "$entity с id=$id не существует")
