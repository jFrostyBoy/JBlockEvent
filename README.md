## JBlockEvent — Плагин для ивентов с сокровищами

**Ядро:** Paper/Spigot  
**Версия:** 1.16.5 — 1.21.1  
**Java:** 16+  
**Зависимость:** DecentHolograms

### Что делает плагин?
#### Создаёт периодические ивенты-сокровища на сервере:

- В заданных локациях спавнится блок (например, маяк, изумрудный блок и т.д.).
- Над блоком появляется голограмма с обратным отсчётом.
- Пока идёт отсчёт — блок **нельзя сломать** и **нельзя открыть инвентарь** (ПКМ отменяется).
- После окончания отсчёта блок становится ломабельным.
- Первый игрок, сломавший блок, получает его в дроп.
- Если никто не сломал блок за отведённое время — он исчезает сам.
- Всё сопровождается звуками, многострочными объявлениями в чат, красивой голограммой и боссбаром с прогрессом.
- Поддержка кастомных имён ивентов, цветов и отображаемых названий миров.

### Установка

1. Скачайте JAR-файл плагина.
2. Положите его в папку `plugins/`.
3. Установите `DecentHolograms` **(обязательно!)**.
4. Перезапустите сервер.
5. В папке `plugins/JBlockEvent/` появится `config.yml` — настройте его.

### Настройка (config.yml)
```yaml
# ========================================================================
#           JBlockEvent — Конфигурационный файл (config.yml)
# ========================================================================
# Этот файл содержит все настройки плагина.
# После изменений сохраните файл и выполните команду /jbe reload.
# ========================================================================

# Смещение голограммы относительно блока ивента
hologram-offset:
  height: 2.5  # Высота над блоком (рекомендуется 2.0 для видимости сверху)
  x: 0.5       # Смещение по оси X (0.5 — центр блока)
  z: 0.5       # Смещение по оси Z (0.5 — центр блока)

# Формат таймера
timer-format:
  minutes: "мин."  # Сокращение для минут (например, "мин." или "м.")
  seconds: "сек."  # Сокращение для секунд (например, "сек." или "с.")
  format: "%m %s"  # Формат вывода таймера (%m — минуты, %s — секунды; можно "%m, %s" или "%m%s")

# Звуковые эффекты для событий
sounds:
  spawn: "ENTITY_ENDER_DRAGON_AMBIENT"  # Звук при появлении блока
  breakable: "BLOCK_NOTE_BLOCK_BELL"    # Звук, когда блок становится доступным
  unbrekable: "ENTITY_VILLAGER_NO"      # Звук при попытке сломать раньше времени
  disappear: "ENTITY_GENERIC_EXPLODE"   # Звук при исчезновении блока
  volume: 1.0                           # Громкость звука (от 0.0 до 2.0)
  pitch: 1.0                            # Тон звука (от 0.5 до 2.0)

# Текст голограммы до открытия
hologram-before:
  - "%display_name%"   # Названием ивента
  - ""
  - "&7До открытия:"
  - "&c%timer_delay%"  # Таймер с обратным отсчётом

# Текст голограммы после открытия
hologram-after:
  - "%display_name%"
  - ""
  - "&7Блок доступен для разрушения!"

# Настройки боссбара (прогресс-бар вверху экрана)
bossbar:
  enabled: true                           # Включить боссбар? (true/false)
  color: "PURPLE"                         # Цвет: RED, BLUE, GREEN, PINK, PURPLE, YELLOW, WHITE
  style: "SEGMENTED_10"                   # Стиль: SOLID, SEGMENTED_6, SEGMENTED_10, SEGMENTED_12, SEGMENTED_20
  protected:                              # Фаза защиты (до открытия)
    text: "%display_name%  &7|  &fМир: %world%  &7|  &fДо открытия: &e%timer_delay%"
    progress: true                        # Показывать прогресс? (true/false)
  breakable:                              # Фаза разрушения (после открытия)
    text: "%display_name%  &7|  &fМир: %world%  &7|  &fДо исчезновения: &e%timer_delay%"
    progress: true                        # Показывать прогресс? (true/false)

# Цвета названий миров
world_colors:
  world: "&a"           # Обычный мир — зелёный
  world_nether: "&c"    # Nether — красный
  world_the_end: "&5"   # End — фиолетовый
  event_world: "&b"     # Евентный мир — голубой

# Отображаемые названия миров
world_names:
  world: "Обычный"              # Отображаемое имя для мира 'world'
  world_nether: "Незер"         # Отображаемое имя для 'world_nether'
  world_the_end: "Энд"          # Отображаемое имя для 'world_the_end'
  event_world: "Евентный мир"   # Отображаемое имя для 'event_world'

# Все сообщения плагина
messages:
  no-permission:
    - "&fУ вас &cнет прав &fна использование этой команды!"
  reload-usage:
    - ""
    - "  &6Помощь по командам:"
    - ""
    - "  &e/jbe reload &7— перезагрузить конфиг"
    - "  &e/jbe start <id> &7— запустить ивент вручную"
    - "  &e/jbe stop <id> &7— остановить ивент"
    - "  &e/eventsdelay (/ed) &7— показать таймеры всех ивентов"
    - ""
  reload-success:
    - "&fКонфигурация &aперезагружена! &fЗагружено ивентов: &a%events%"
  protected-break:
    - ""
    - "  &fЭтот блок ещё &cнельзя &fломать!"
    - "  &fПодождите: &c%timer_delay%"
    - ""
  break-success-broadcast:
    - ""
    - "  &fИвент %display_name% &fзавершён!"
    - "  &fРазрушен игроком: &a%player%"
    - ""
  event-start-broadcast:
    - ""
    - "  &fИвент %display_name% &fзаспавнился"
    - "  &fв мире: %world% &fна координатах: &a%x% %y% %z%"
    - ""
  event-end-broadcast:
    - ""
    - "  &fИвент %display_name% &fзавершился!"
    - "  &cНикто &fне сломал блок и он &cисчез!"
    - ""
  invalid-event-number:
    - "&cНеверный &fномер ивента: &c%number%"
  event-already-active:
    - "&fИвент #%number% &aуже активен!"
  event-started-manually:
    - "&fИвент #%number% &aзапущен &fвручную"
  event-stopped:
    - "&fИвент #%id% %display_name% &fбыл &cостановлен"
  eventsdelay-header:
    - ""
    - "  &r&6&l&m                &r  &fТаймеры ивентов &7[ &e%total% &7]  &r&6&l&m                &r"
    - ""
  eventsdelay-inactive:
    - "  &f#%id% %display_name% &8— &7спавн через: &f%time%"
  eventsdelay-active-protected:
    - "  &f#%id% %display_name% &8— &7можно сломать через: &c%time%"
  eventsdelay-active-breakable:
    - "  &f#%id% %display_name% &8— &7доступен на координатах: &a%x% %y% %z% &7в мире: %world%"
  eventsdelay-footer:
    - ""
    - "  &r&6&l&m                                                          &r"
    - ""
  eventsdelay-no-permission:
    - "&fУ вас &cнет прав &fна просмотр таймеров!"

# Список всех ивентов (добавьте столько, сколько нужно)
# Каждый ивент имеет уникальный ID (ключ, например, 1, 2, 3...)
events:
  1:                                               # Ивент №1 (пример)
    location: "world:100:64:200"                   # Формат: мир:x:y:z (координаты блока)
    display_name: "&6✦ &e&lᴋᴘиᴄтᴀлл ᴄилы &6✦"     # Отображаемое имя (с цветами)
    block-material: "BEACON"                       # Материал блока (из Minecraft)
    spawn-interval-minutes: 30                     # Интервал появления в минутах (минимум 1)
    breakable-delay-seconds: 300                   # Задержка защиты в секундах (минимум 0)
    disappear-seconds: 600                         # Общее время жизни в секундах (минимум breakable-delay + 60)

  2:                                               # Ивент №2 (пример)
    location: "world_nether:-50:70:300"            # Координаты в Nether
    display_name: "&4✦ &c&lᴀдᴄᴋий ᴏбᴇлиᴄᴋ &4✦"    # Имя с красным цветом
    block-material: "NETHERITE_BLOCK"              # Материал
    spawn-interval-minutes: 45                     # Интервал
    breakable-delay-seconds: 420                   # Задержка защиты
    disappear-seconds: 900                         # Время жизни
```

### Права (permissions)

`jblockevent.admin` — полный доступ к админ-командам (по умолчанию op).  
`jblockevent.player` — доступ к /eventsdelay (по умолчанию всем).  

### Команды
#### Для игроков:

`/eventsdelay (или /ed)` — показывает статус всех ивентов:
- время до следующего спавна (если не активен)
- оставшееся время до ломания (если активен и защищён)
- "УЖЕ МОЖНО ЛОМАТЬ!" + координаты (если разблокирован)


#### Для админов:

`/jbe reload` — перезагружает config.yml (новые ивенты, сообщения, звуки). Активные ивенты НЕ исчезают и продолжают работать!  
`/jbe start <номер>` — принудительно спавнит ивент по номеру из конфига (есть таб-комплит)  
`/jbe stop <номер>` — принудительно останавливает активный ивент  


<img width="1029" height="271" alt="Screenshot_1" src="https://github.com/user-attachments/assets/5a92d7f1-61d4-43ad-915d-f3f423e41e71" />
<img width="1022" height="264" alt="Screenshot_3" src="https://github.com/user-attachments/assets/722fb7cc-fe5d-46f0-aad0-3f366dfa1641" />
<img width="1032" height="288" alt="Screenshot_5" src="https://github.com/user-attachments/assets/bc93501c-828d-4fbe-9b56-d65e84192efb" />
<img width="1046" height="290" alt="Screenshot_7" src="https://github.com/user-attachments/assets/b4a311a0-5613-4a1f-9c68-daad7a84dba4" />
<img width="1919" height="890" alt="Screenshot_4" src="https://github.com/user-attachments/assets/0c6c23a5-7810-42ed-8275-a28010431e0d" />
<img width="1919" height="600" alt="Screenshot_6" src="https://github.com/user-attachments/assets/706fa08a-7d19-42a0-9f6c-305007a17d20" />
<img width="1919" height="922" alt="Screenshot_8" src="https://github.com/user-attachments/assets/419634b1-adb5-4d68-8a07-fadc4339aa7f" />
<img width="1025" height="123" alt="Screenshot_9" src="https://github.com/user-attachments/assets/397ed20d-cb7b-4110-b0ba-e059d67862c8" />
<img width="1022" height="160" alt="Screenshot_10" src="https://github.com/user-attachments/assets/2db83b07-ac09-4fa4-a8a3-4262f01e94e1" />
<img width="1022" height="175" alt="Screenshot_2" src="https://github.com/user-attachments/assets/b3ef011e-819d-450a-8802-98f0734578bc" />
<img width="1024" height="148" alt="Screenshot_11" src="https://github.com/user-attachments/assets/26bbfa9c-1a99-4288-8f5c-c75fa88527ea" />
