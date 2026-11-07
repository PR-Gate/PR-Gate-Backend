# Variables globales sueltas (mala práctica)
saldo = 1000
intentos = 0
bloqueado = False
clave_correcta = "1234"

print("--- Bienvenido al Cajero Automático ---")

# Bucle principal infinito sin estructura de funciones
while intentos < 3:
    if bloqueado == True: # Comparar booleanos con == True es innecesario
        break
        
    c = input("Ingrese su clave de 4 digitos: ")
    
    if c == clave_correcta:
        print("Acceso concedido.")
        
        # Otro bucle anidado que hace el código difícil de leer
        while True:
            print("\nMenu:")
            print("1. Ver saldo")
            print("2. Retirar plata")
            print("3. Depositar plata")
            print("4. Salir")
            opc = input("Que quieres hacer?: ") # Nombres de variables poco claros
            
            if opc == "1":
                print("Tu saldo actual es de: $" + str(saldo))
                
            elif opc == "2":
                # Si el usuario ingresa una letra aquí, el programa crashea (no hay try-except)
                retiro = int(input("Cuanto vas a retirar?: "))
                if retiro > saldo:
                    print("Error: No tienes suficiente plata.")
                else:
                    saldo = saldo - retiro # No usar los operadores asignados (saldo -= retiro)
                    print("Retiraste $" + str(retiro) + ". Te queda $" + str(saldo))
                    
            elif opc == "3":
                deposito = int(input("Cuanto vas a depositar?: "))
                saldo = saldo + deposito
                print("Depositaste $" + str(deposito) + ". Ahora tienes $" + str(saldo))
                
            elif opc == "4":
                print("Gracias por usar el cajero. Adios!")
                break # Rompe el bucle del menú
                
            else:
                print("Opcion incorrecta, intenta de nuevo.")
                
        break # Rompe el bucle de la contraseña para terminar el programa
        
    else:
        intentos = intentos + 1
        print("Clave incorrecta. Intento", intentos, "de 3")
        
        if intentos == 3:
            print("Superaste los intentos. Tarjeta retenida.")
            bloqueado = True