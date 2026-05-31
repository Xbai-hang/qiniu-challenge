<template>
  <article :class="['voice-chat-message', message.role, { voice: isVoiceUserMessage }]">
    <div class="voice-chat-meta">
      <span>{{ message.role === 'user' ? '你' : 'AI' }}</span>
      <small v-if="isVoiceUserMessage">语音消息</small>
      <small v-else-if="message.role === 'assistant'">文字回复</small>
    </div>

    <div v-if="isVoiceUserMessage" class="voice-chat-audio-row">
      <button type="button" class="voice-chat-play" aria-label="播放语音消息" @click="$emit('playUser', message)">
        <VideoPlay />
      </button>
      <div class="voice-chat-wave" aria-hidden="true">
        <span v-for="index in 16" :key="index"></span>
      </div>
    </div>

    <p v-if="message.role === 'assistant'" class="voice-chat-text">{{ message.content }}</p>

    <div v-if="isVoiceUserMessage" class="voice-chat-transcript">
      <span>转写文本</span>
      <p>{{ message.transcriptText || message.content }}</p>
    </div>

    <p v-else-if="message.role === 'user'" class="voice-chat-text">{{ message.content }}</p>

    <button
      v-if="message.role === 'assistant'"
      type="button"
      class="voice-chat-tts"
      :disabled="isPreparing"
      @click="isSpeaking ? $emit('pauseAssistant') : $emit('playAssistant', message)"
    >
      <VideoPause v-if="isSpeaking" />
      <VideoPlay v-else />
      <span>{{ isSpeaking ? '暂停语音' : isPreparing ? '生成语音' : '播放语音' }}</span>
    </button>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { VideoPause, VideoPlay } from '@element-plus/icons-vue'
import type { AssistantChatMessage } from '../composables/useAiAssistantSession'

const props = defineProps<{
  message: AssistantChatMessage
  preparingMessageId?: number
  speakingMessageId?: number
}>()

defineEmits<{
  playUser: [message: AssistantChatMessage]
  playAssistant: [message: AssistantChatMessage]
  pauseAssistant: []
}>()

const isVoiceUserMessage = computed(() => props.message.role === 'user' && props.message.inputMode === 'voice')
const isPreparing = computed(() => props.preparingMessageId === props.message.id)
const isSpeaking = computed(() => props.speakingMessageId === props.message.id)
</script>
