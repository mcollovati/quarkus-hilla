import {AutoForm} from '@hilla/react-crud';
import {Notification} from '@vaadin/notification';
import {UserService} from 'Frontend/generated/endpoints';
import UserModel from 'Frontend/generated/com/example/application/autocrud/UserModel';
import User from "Frontend/generated/com/example/application/autocrud/User";
import {Button} from '@hilla/react-components/Button.js';
import {useState} from 'react';

export default function AutoFormView() {

    const [editedItem, setEditedItem] = useState<User | null>(null);

    function onUserSaved({item}: { item: User }): void {

        Notification.show(`Saved user ${item.name} ${item.surname} with id ${item.id}`);
    }

    function loadUser(id: number): void {
        UserService.get(id).then(user => user && setEditedItem(user));
    }

    return <div>
        <Button id="user51" onClick={(e) => loadUser(51)}>Load Lillian</Button>
        <Button id="user48" onClick={(e) => loadUser(48)}>Load Victoria</Button>
        <AutoForm service={UserService} model={UserModel} item={editedItem} onSubmitSuccess={onUserSaved}/>
    </div>;

}


