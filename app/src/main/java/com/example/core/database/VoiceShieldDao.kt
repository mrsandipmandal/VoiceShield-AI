package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceShieldDao {

    // --- Voice Messages ---
    @Query("SELECT * FROM voice_messages ORDER BY callDateMillis DESC")
    fun observeAllMessages(): Flow<List<VoiceMessageEntity>>

    @Query("SELECT * FROM voice_messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): VoiceMessageEntity?

    @Query("SELECT * FROM voice_messages ORDER BY callDateMillis DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<VoiceMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: VoiceMessageEntity)

    @Update
    suspend fun updateMessage(message: VoiceMessageEntity)

    @Query("UPDATE voice_messages SET isRead = :isRead WHERE id = :messageId")
    suspend fun markMessageRead(messageId: String, isRead: Boolean)

    @Query("UPDATE voice_messages SET isImportant = :isImportant WHERE id = :messageId")
    suspend fun markMessageImportant(messageId: String, isImportant: Boolean)

    @Query("UPDATE voice_messages SET transcription = :transcription WHERE id = :messageId")
    suspend fun updateMessageTranscription(messageId: String, transcription: String)

    @Query("DELETE FROM voice_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM voice_messages")
    suspend fun deleteAllMessages()

    @Query("SELECT * FROM voice_messages WHERE callDateMillis < :cutoffMillis AND isImportant = 0")
    suspend fun getExpiredMessages(cutoffMillis: Long): List<VoiceMessageEntity>

    // --- Screened Calls History ---
    @Query("SELECT * FROM screened_calls ORDER BY timestampMillis DESC")
    fun observeScreenedCalls(): Flow<List<ScreenedCallEntity>>

    @Query("SELECT * FROM screened_calls WHERE callerNumber = :number AND timestampMillis >= :sinceMillis")
    suspend fun getRecentCallsFromNumber(number: String, sinceMillis: Long): List<ScreenedCallEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScreenedCall(call: ScreenedCallEntity): Long

    @Query("DELETE FROM screened_calls")
    suspend fun clearScreenedCalls()

    // --- Caller Rules (Allowlist, Blocklist, User-Reported Spam) ---
    @Query("SELECT * FROM caller_rules ORDER BY createdAtMillis DESC")
    fun observeCallerRules(): Flow<List<CallerRuleEntity>>

    @Query("SELECT * FROM caller_rules WHERE ruleType = :ruleType ORDER BY createdAtMillis DESC")
    fun observeRulesByType(ruleType: String): Flow<List<CallerRuleEntity>>

    @Query("SELECT * FROM caller_rules")
    suspend fun getAllCallerRulesSnapshot(): List<CallerRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallerRule(rule: CallerRuleEntity): Long

    @Query("DELETE FROM caller_rules WHERE id = :ruleId")
    suspend fun deleteCallerRuleById(ruleId: Long)

    // --- Greeting Scripts ---
    @Query("SELECT * FROM greeting_scripts ORDER BY updatedAtMillis DESC")
    fun observeGreetingScripts(): Flow<List<GreetingScriptEntity>>

    @Query("SELECT * FROM greeting_scripts")
    suspend fun getAllGreetingScriptsSnapshot(): List<GreetingScriptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGreetingScript(script: GreetingScriptEntity)

    @Query("DELETE FROM greeting_scripts WHERE id = :scriptId")
    suspend fun deleteGreetingScript(scriptId: String)

    // --- Local TTS Voice Models ---
    @Query("SELECT * FROM voice_models ORDER BY languageCode ASC, displayName ASC")
    fun observeVoiceModels(): Flow<List<VoiceModelEntity>>

    @Query("SELECT * FROM voice_models")
    suspend fun getAllVoiceModelsSnapshot(): List<VoiceModelEntity>

    @Query("SELECT * FROM voice_models WHERE id = :modelId LIMIT 1")
    suspend fun getVoiceModelById(modelId: String): VoiceModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceModels(models: List<VoiceModelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceModel(model: VoiceModelEntity)
}
