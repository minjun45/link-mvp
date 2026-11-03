import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
export interface Spec extends TurboModule {
  readiness(): Promise<string>;
  requestPermissions(): Promise<boolean>;
  requestBluetooth(): Promise<boolean>;
  scan(namesJson: string): Promise<string>;
  cancelScan(): void;
  openSettings(kind: string): void;
  signal(kind: string): void;
  speak(text: string): void;
  stopSpeaking(): void;
  recognize(): Promise<string>;
  openPayment(url: string, base: string): Promise<string>;
}
export default TurboModuleRegistry.get<Spec>('LinkDevice');
