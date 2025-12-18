## JBlockEvent — Плагин для ивентов с сокровищами

**Ядро:** Paper/Spigot  
**Версия:** 1.16.5 — 1.21.1  
**Java:** 16+  
**Зависимость:** DecentHolograms

### Что делает плагин?

JBlockEvent создаёт периодические ивенты-сокровища на сервере:

- В заданных локациях спавнится блок (например, маяк, изумрудный блок и т.д.).
- Над блоком появляется голограмма с обратным отсчётом.
- Пока идёт отсчёт — блок **нельзя сломать** и **нельзя открыть инвентарь** (ПКМ отменяется).
- После окончания отсчёта блок становится ломабельным.
- Первый игрок, сломавший блок, получает его в дроп (натуральный дроп с учётом инструмента, Fortune и т.д.).
- Если никто не сломал блок за отведённое время — он исчезает сам.
- Всё сопровождается звуками, многострочными объявлениями в чат и красивой голограммой.

Идеально для ежедневных/еженедельных ивентов, чтобы мотивировать игроков исследовать карту.

### Установка

1. Скачайте JAR-файл плагина.
2. Положите его в папку `plugins/`.
3. Установите `DecentHolograms` **(обязательно!)**.
4. Перезапустите сервер.
5. В папке `plugins/JBlockEvent/` появится `config.yml` — настройте его.

### Настройка (config.yml)

```yaml
# Ивенты (номер — ключ)
events:
  1:
    location: "world:100:64:200"
    block-material: BEACON
    spawn-interval-minutes: 10
    breakable-delay-seconds: 300
    disappear-seconds: 600

  2:
    location: "world:-50:70:300"
    block-material: EMERALD_BLOCK
    spawn-interval-minutes: 20
    breakable-delay-seconds: 180
    disappear-seconds: 360

# Голограммы
hologram-before:
  - "&6&lСОКРОВИЩЕ!"
  - "&eПодождите появления..."
  - ""
  - "&fДо ломания: &c%timer_delay%"

hologram-after:
  - "&c&lЛОМАЙ СЕЙЧАС!"
  - "&a&lБлок доступен!"

# Смещение голограммы относительно центра блока
hologram-offset:
  height: 2.5    # Высота над блоком (от центра блока)
  x: 0.5         # Смещение по X (0.5 — центр)
  z: 0.5         # Смещение по Z (0.5 — центр)

# Формат таймера
timer-format:
  minutes-singular: "минута"
  minutes-plural: "минуты"
  minutes-many: "минут"
  seconds-singular: "секунда"
  seconds-plural: "секунды"
  seconds-many: "секунд"
  separator: " "
  only-seconds: "сек."

# Звуки
sounds:
  spawn: "ENTITY_ENDER_DRAGON_AMBIENT"
  breakable: "BLOCK_NOTE_BLOCK_BELL"
  disappear: "ENTITY_GENERIC_EXPLODE"
  volume: 1.0
  pitch: 1.0

# Многострочные сообщения
messages:
  no-permission:
    - "&cУ вас нет прав на эту команду!"
  reload-usage:
    - ""
    - "&eИспользование команд:"
    - " "
    - "&a/jbe reload &7— перезагрузка плагина"
    - "&a/jbe start <номер> &7— запустить ивент вручную"
    - "&a/jbe stop <номер> &7— остановить ивент вручную"
    - "&a/jbe eventsdelay &7— время до следующих ивентов"
    - " "
  reload-success:
    - "&aJBlockEvent успешно перезагружен!"
    - "&eАктивных ивентов в конфиге: &a%events%"
  protected-break:
    - "&cПодожди! Ещё нельзя ломать блок."
  break-success-broadcast:
    - ""
    - "&a✦ &lИГРОК %player% СЛОМАЛ СОКРОВИЩЕ! &a✦"
    - "&7Поздравляем с победой!"
    - ""
  event-start-broadcast:
    - ""
    - "&e⚡ &lНОВОЕ СОКРОВИЩЕ ПОЯВИЛОСЬ! ⚡"
    - "&7Мир: &f%world%"
    - "&7Координаты: &f%x% %y% %z%"
    - "&7Успейте сломать первым!"
    - ""
  event-end-broadcast:
    - ""
    - "&c☠ &lСОКРОВИЩЕ ИСЧЕЗЛО ☠"
    - "&7Никто не успел его сломать..."
    - "&7Ждём следующий ивент!"
    - ""
  invalid-event-number:
    - "&cИвент с номером &e%number% &cне найден в конфиге!"
  event-already-active:
    - "&cИвент №%number% уже активен!"
  event-started-manually:
    - "&aИвент №%number% успешно запущен вручную!"
  event-stopped:
    - "&cИвент №%id% был принудительно остановлен!"
  eventsdelay-header:
    - ""
    - "&e&l≫ СТАТУС СОКРОВИЩ ≪"
    - ""
  eventsdelay-inactive:
    - "&7• Ивент &a#%id% &7(&f%material%&7): следующий спавн через &e%time%"
  eventsdelay-active-protected:
    - "&7• Ивент &a#%id% &7(&f%material%&7): можно ломать через &e%time%"
  eventsdelay-active-breakable:
    - "&7• Ивент &a#%id% &7(&f%material%&7): &a&lУЖЕ МОЖНО ЛОМАТЬ! &7(&f%x% %y% %z%&7)"
  eventsdelay-footer:
    - ""
    - "&eВсего ивентов: &a%total%"
  eventsdelay-no-permission:
    - "&cУ вас нет прав на эту команду!"
```

### Права (permissions)

- `jblockevent.admin` — полный доступ к админ-командам (по умолчанию op).
- `jblockevent.player` — доступ к /eventsdelay (по умолчанию всем).

### Команды
#### Для игроков:

- `/eventsdelay (или /ed)` — показывает статус всех ивентов:
- время до следующего спавна (если не активен)
- оставшееся время до ломания (если активен и защищён)
- "УЖЕ МОЖНО ЛОМАТЬ!" + координаты (если разблокирован)


#### Для админов:

- `/jbe reload` — перезагружает config.yml (новые ивенты, сообщения, звуки). Активные ивенты НЕ исчезают и продолжают работать!
- `/jbe start <номер>` — принудительно спавнит ивент по номеру из конфига (таб-комплит).

- `/jbe stop <номер>` — принудительно останавливает активный ивент

  
<img width="545" height="137" alt="Screenshot_5" src="https://github.com/user-attachments/assets/58d3f6dc-ae79-4bc2-be52-e4831797afa5" />
<img width="713" height="90" alt="Screenshot_4" src="https://github.com/user-attachments/assets/6ebcb71a-0b03-476f-867d-91648e2754ff" />
<img width="1020" height="171" alt="Screenshot_3" src="https://github.com/user-attachments/assets/78304c15-31b4-4947-96cd-5e9aca0ad1ec" />
<img width="560" height="593" alt="Screenshot_2" src="https://github.com/user-attachments/assets/4694993b-0aa0-4179-a765-b588a933d618" />
<img width="1919" height="908" alt="Screenshot_1" src="https://github.com/user-attachments/assets/8a65975c-abd0-4303-8bd2-d2cb1d361550" />
<img width="841" height="224" alt="Screenshot_6" src="https://github.com/user-attachments/assets/095864d1-12ce-4584-8f82-e806deb33362" />

