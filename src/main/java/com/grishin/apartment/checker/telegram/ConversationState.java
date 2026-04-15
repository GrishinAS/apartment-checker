package com.grishin.apartment.checker.telegram;

enum ConversationState {
    IDLE,
    WAITING_FOR_COMMUNITY,
    FILTER_MENU,
    WAITING_FOR_AMENITIES,
    SETTING_MIN_PRICE,
    SETTING_MAX_PRICE,
    SETTING_MIN_DATE,
    SETTING_MAX_DATE,
    SETTING_UNIT_TYPE,
    SETTING_MIN_FLOOR,
    SETTING_MAX_FLOOR
}
