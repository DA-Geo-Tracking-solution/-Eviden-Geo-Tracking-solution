import { Injectable } from '@angular/core';
import { Client, IMessage, Stomp, StompConfig } from '@stomp/stompjs';
// import * as SockJS from 'sockjs-client';
import SockJS from 'sockjs-client';
import { BehaviorSubject } from 'rxjs';
import { KeycloakService } from '../keycloak/keycloak.service';

@Injectable({
  providedIn: 'root',
})
export class WebsocketService {
  private stompClient!: Client;
  private connectedSubject = new BehaviorSubject<boolean>(false);
  public connected$ = this.connectedSubject.asObservable();

  constructor(private keycloakService: KeycloakService) {}

  connect(): boolean {
    const token = this.keycloakService.profile?.token;
    if (token) {
      this.stompClient = new Client({
        brokerURL: 'ws://localhost:8080/ws/chat', // Update with your WebSocket endpoint
        connectHeaders: {
          Authorization: `Bearer ${token}`, // Add JWT token in headers
        },
        debug: (str) => console.log(str),
        reconnectDelay: 5000, // Automatically reconnect after 5 seconds
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
      });

      /*const socket = new SockJS("/ws");
      const stompClient2 = Stomp.over(socket);*/
      
      /*const socket = new WebSocket('ws://localhost:8080/ws');
      const stompClient = Stomp.over(socket);*/

      this.stompClient.onConnect = () => {
        console.log('Connected');
        this.connectedSubject.next(true);
      };
  
      this.stompClient.onStompError = (frame) => {
        console.error('STOMP error', frame);
      };
  
      this.stompClient.activate();
      return true;
    }
    return false
  }

  disconnect(): void {
    if (this.stompClient) {
      this.stompClient.deactivate();
    }
    this.connectedSubject.next(false);
  }

  subscribe(destination: string, callback: (message: IMessage) => void): void {
    if (this.stompClient && this.stompClient.connected) {
      this.stompClient.subscribe(destination, callback);
    } else {
      this.stompClient.onConnect = () => {
        this.stompClient.subscribe(destination, callback);
        this.connectedSubject.next(true);
      };
    }
  }

  send(destination: string, body: any): void {
    if (this.stompClient && this.stompClient.connected) {
      this.stompClient.publish({
        destination,
        body: JSON.stringify(body),
      });
    } else {
      console.error('Cannot send, client is not connected');
    }
  }
}
