import { onBeforeUnmount, ref } from 'vue'

export function useVoiceRecorder(onRecorded: (audio: Blob) => void | Promise<void>) {
  const isRecording = ref(false)
  const recordingError = ref('')
  const mediaRecorder = ref<MediaRecorder | null>(null)
  const mediaStream = ref<MediaStream | null>(null)
  let audioChunks: BlobPart[] = []

  async function startRecording() {
    recordingError.value = ''

    if (isRecording.value) {
      return
    }

    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      recordingError.value = '当前浏览器不支持录音，请使用文本输入'
      return
    }

    try {
      mediaStream.value = await navigator.mediaDevices.getUserMedia({ audio: true })
      audioChunks = []
      const mimeType = preferredAudioMimeType()
      const recorder = new MediaRecorder(mediaStream.value, mimeType ? { mimeType } : undefined)
      mediaRecorder.value = recorder

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          audioChunks.push(event.data)
        }
      }
      recorder.onerror = () => {
        recordingError.value = '录音过程中断，请重试'
        cleanupRecorder()
      }
      recorder.onstop = () => {
        const audio = new Blob(audioChunks, { type: recorder.mimeType || 'audio/webm' })
        cleanupRecorder()
        if (audio.size > 0) {
          void onRecorded(audio)
        }
      }

      recorder.start()
      isRecording.value = true
    } catch (error) {
      cleanupRecorder()
      recordingError.value = error instanceof Error ? error.message : '无法访问麦克风'
    }
  }

  function stopRecording() {
    if (mediaRecorder.value && mediaRecorder.value.state !== 'inactive') {
      mediaRecorder.value.stop()
      return
    }
    cleanupRecorder()
  }

  async function toggleRecording() {
    if (isRecording.value) {
      stopRecording()
      return
    }
    await startRecording()
  }

  function cleanupRecorder() {
    mediaStream.value?.getTracks().forEach((track) => track.stop())
    mediaStream.value = null
    mediaRecorder.value = null
    isRecording.value = false
  }

  onBeforeUnmount(cleanupRecorder)

  return {
    isRecording,
    recordingError,
    startRecording,
    stopRecording,
    toggleRecording,
    cleanupRecorder,
  }
}

function preferredAudioMimeType() {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus']
  return candidates.find((type) => MediaRecorder.isTypeSupported(type)) || ''
}

export function shouldIgnoreVoiceShortcut(event: KeyboardEvent) {
  if (event.code !== 'Space') {
    return true
  }
  const target = event.target as HTMLElement | null
  if (!target) {
    return false
  }
  return Boolean(target.closest('input, textarea, select, [contenteditable="true"], button, a'))
}
